import java.util.Random ;

class Laboratorio{
    private int numComputers = 20 ;
    public int prof_q = 0 , tesisti_q = 0 ;
    // false = libero , true = occupato
    private boolean[] computers = new boolean[numComputers] ;

    //Richieste di accesso
    public synchronized void accediProfessore() throws InterruptedException {
        while(isLabOccupato()){
            wait() ; //aspetto che il lab si liberi
        }
        System.out.println("Professore accede");
        Thread.sleep(2000) ;
        System.out.println("Professore logout");
        notifyAll();
        prof_q -= 1 ;
    }

    public synchronized void accediTesista(int computerIndex) throws InterruptedException {
        // true = occupato
        while(computers[computerIndex] || prof_q > 0){
            wait() ;
        }
        //occupa il computer, lavora e rilascia
        computers[computerIndex] = true;
        System.out.println("Tesista accede al pc: "+computerIndex);
        Thread.sleep(2000) ;
        System.out.println("Tesista logout dal pc: "+computerIndex);
        computers[computerIndex] = false;
        notifyAll();
        tesisti_q -= 1 ;
    }

    public synchronized void accediStudente() throws InterruptedException {
        //cerca un pc libero
        int freePc = getFreePc();
        while(freePc == -1 || prof_q > 0 || tesisti_q > 0){
            wait() ;
            freePc = getFreePc();
        }

        computers[freePc] = true;
        System.out.println("Studente accede al pc: "+freePc);
        Thread.sleep(2000) ;
        System.out.println("Studente logout dal pc: "+freePc);
        computers[freePc] = false;
        notifyAll();
    }

    // se c'e' almeno un computer libero il lab non e' occupato
    public boolean isLabOccupato(){
        for(boolean computer : computers){
            if(computer == false) return false ;
        }
        return true ;
    }

    public int getFreePc() {
        for(int i = 0 ; i < 20 ; i++){
            if(computers[i] == false){
                return i ;
            }
        }
        return -1 ;
    }
}

class Utente implements Runnable{
    private Laboratorio laboratorio ;
    private String tipo ;
    private int computerIndex ;
    private int accessCount ;

    public Utente(Laboratorio laboratorio, String tipo , int computerIndex){
        this.laboratorio = laboratorio ;
        this.tipo = tipo ;
        this.computerIndex = computerIndex ;
        this.accessCount = new Random().nextInt(3) + 1 ; //accedono da 1 a 3 volte
    }

    public void run(){
        try{
            for(int i = 0 ; i < accessCount ; i++){
                switch (tipo){
                    case "professore":
                        laboratorio.prof_q += 1 ;
                        laboratorio.accediProfessore();
                        break;
                    case "tesista":
                        laboratorio.tesisti_q += 1 ;
                        laboratorio.accediTesista(computerIndex);
                        break;
                    case "studente":
                        laboratorio.accediStudente();
                        break;
                }
                Thread.sleep(new Random().nextInt(2000) + 1000) ;
            }
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
}


public class Assignment5_LaboratorioPc {
    public static void main(String[] args) {
        Laboratorio laboratorio = new Laboratorio() ;

        //Professori
        for(int i = 0 ; i < 2 ; i++){
            new Thread(new Utente(laboratorio, "professore", -1)).start();
        }
        //Tesisti
        for(int i = 0 ; i < 3 ; i++){
            new Thread(new Utente(laboratorio, "tesista", new Random().nextInt(20))).start();
        }

        //Studenti
        for(int i = 0 ; i < 3 ; i++){
            new Thread(new Utente(laboratorio, "studente", new Random().nextInt(20))).start();
        }
    }
}
