import java.io.*;
import java.net.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import Settings.ServerSettings;
import com.google.gson.*;


public class ServerMain {

    public static ConcurrentHashMap<String,User> clientsUsernames = new ConcurrentHashMap<>();
    public static OrderBook orderBook = new OrderBook();
    public static final Object orderBookLock = new Object();
    public static final Object credentialsFileLock = new Object();
    public static final Object stopOrdersLock = new Object();
    public static final Object storicoTransazioniLock = new Object();
    public static JsonArray storicoTransazioni = new JsonArray();
    public static DatagramSocket udpSocket ;

    public static void main(String[] args) {
        System.out.println("Working Directory = " + System.getProperty("user.dir"));

        loadCredentialsFromFile();
        loadStoricoFromFile();

        //definisco i parametri per la socket
        //todo define server ip address
        int port = Settings.ServerSettings.TCPPORT ;

        //creo la socket UDP per le notifiche
        try {
            udpSocket = new DatagramSocket(); // Inizializzazione della socket UDP
        } catch (SocketException e) {
            System.err.println("Errore nella creazione della socket UDP");
            e.printStackTrace();
            return;
        }


        //CachedThreadPool per gestire i client
        ExecutorService threadPool = Executors.newCachedThreadPool();
        //Aggiungo alla threadPool il thread stopOrderHandler
        threadPool.execute(new stopOrdersHandler());

        try(ServerSocket serverSocket = new ServerSocket(port)){
            System.out.println("Server in ascolto sulla porta " + port);
            while(true){
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connesso : "+ clientSocket.getInetAddress());
                //Creo un thread nella thread pool per gestire il client
                threadPool.submit(new ClientHandler(clientSocket));
            }
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            threadPool.shutdown();
        }
    }

    //Fun to load credentials from file
    private static void loadCredentialsFromFile() {
        System.out.println("[DEBUG] locking file");
        synchronized (credentialsFileLock) {
            try (BufferedReader reader = new BufferedReader(new FileReader(Settings.ServerSettings.credentialsFilePath))) {
                StringBuilder content = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }

                JsonArray credentialsArray = JsonParser.parseString(content.toString()).getAsJsonArray();

                for (int i = 0; i < credentialsArray.size(); i++) {
                    JsonObject userObj = credentialsArray.get(i).getAsJsonObject();
                    String username = userObj.get("username").getAsString();
                    String password = userObj.get("password").getAsString();

                    // Crea un nuovo oggetto User senza associare un socket
                    User user = new User(username, password, null);
                    ServerMain.clientsUsernames.put(username, user);
                }

                System.out.println("Credenziali caricate correttamente da " + Settings.ServerSettings.credentialsFilePath);
            } catch (FileNotFoundException e) {
                System.err.println("File " + Settings.ServerSettings.credentialsFilePath + " non trovato.");
            } catch (IOException e) {
                System.err.println("Errore nella lettura del file " + Settings.ServerSettings.credentialsFilePath);
            } catch (Exception e) {
                System.err.println("Errore sconosciuto durante il caricamento delle credenziali: " + e.getMessage());
            }
        }
        System.out.println("[DEBUG] releasing file");
    }
    //Fun to save credentials to file
    public static void saveCredentialsToFile() {
        System.out.println("[DEBUG] locking file");
        synchronized (credentialsFileLock) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(Settings.ServerSettings.credentialsFilePath))) {
                JsonArray credentialsArray = new JsonArray();

                clientsUsernames.forEach((username, user) -> {
                    JsonObject userObj = new JsonObject();
                    userObj.addProperty("username", user.username);
                    userObj.addProperty("password", user.password);
                    credentialsArray.add(userObj);
                });

                writer.write(credentialsArray.toString());
                System.out.println("Credenziali salvate correttamente in " + Settings.ServerSettings.credentialsFilePath);
            } catch (IOException e) {
                System.err.println("Errore durante il salvataggio delle credenziali: " + e.getMessage());
            }
        }
        System.out.println("[DEBUG] releasing file");
    }
    //Fun to load storicoTransazioni
    public static void loadStoricoFromFile() {
        try(FileReader reader = new FileReader(Settings.ServerSettings.storicoOrdiniFilePath)){
            JsonObject temp = JsonParser.parseReader(reader).getAsJsonObject();
            storicoTransazioni = temp.get("trades").getAsJsonArray();
            System.out.println("Loaded storico transazioni len=" + storicoTransazioni.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //Fun to append new trans to array
    public static void appendToStoricoTransazioni(long orderId , String type, String orderType, Integer size, Integer price, long timestamp ) {
        JsonObject newOrder = new JsonObject();
        newOrder.addProperty("orderID", orderId);
        newOrder.addProperty("type", type);
        newOrder.addProperty("orderType", orderType);
        newOrder.addProperty("size", size);
        newOrder.addProperty("price", price);
        newOrder.addProperty("timestamp", System.currentTimeMillis());

        //Append the new object to the array
        synchronized (storicoTransazioniLock) {
            ServerMain.storicoTransazioni.add(newOrder);
        }
    }
    //Fun to save storico transazioni
    public static void saveStoricoTransazioni() {
        try(FileWriter writer = new FileWriter(Settings.ServerSettings.storicoOrdiniFilePath)){
            synchronized (storicoTransazioniLock) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create() ;
                JsonObject temp = new JsonObject();
                temp.add("trades" , storicoTransazioni);
                gson.toJson(temp, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //Create messaggio di notifica
    public static String createUdpMessage(long orderId , String type , String orderType, Integer size , Integer price, long timestamp){
        JsonObject udpMessage = new JsonObject();
        udpMessage.addProperty("orderId" , orderId);
        udpMessage.addProperty("type", type);
        udpMessage.addProperty("orderType", orderType);
        udpMessage.addProperty("size", size);
        udpMessage.addProperty("price", price);
        udpMessage.addProperty("timestamp" , timestamp);
        return udpMessage.toString();
    }

    //Notifica Utenti
    public static void notifyUser(String username, String message) {
        User user = ServerMain.clientsUsernames.get(username);
        if (user != null && user.socket != null) {
            try {
                InetAddress address = user.socket.getInetAddress();
                int udpPort = user.udpPort;
                byte[] buffer = ("\n"+message).getBytes();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, udpPort);
                ServerMain.udpSocket.send(packet);
                System.out.println("Notifica UDP inviata a " + username);
            } catch (IOException e) {
                System.err.println("Errore nell'invio della notifica UDP a " + username);
                e.printStackTrace();
            }
        } else {
            System.err.println("Utente " + username + " non trovato o non connesso.");
        }
    }

}

class ClientHandler implements Callable<Void> {

    User loggedUser;

    private final Socket clientSocket;
    private DatagramSocket udpSocket;
    private BufferedReader in;
    private PrintWriter out;
    private int udpPort;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
        try{
            this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public Void call(){
        try {
            //riceve la porta udp come primo messaggio
            String udpPortMessage = in.readLine();
            udpPort = Integer.parseInt(udpPortMessage);
            System.out.println("[DEBUG] udpPort: " + udpPort);

            String message;
            while ((message = this.in.readLine()) != null) {
                System.out.println("Messaggio ricevuto dal client: " + message);

                // Parse JSON ricevuto dal client usando Gson
                JsonObject msg = JsonParser.parseString(message).getAsJsonObject();

                //Gestione del messaggio in base al tipo
                String msgtype = msg.get("msgtype").getAsString() ;
                if(msg.get("msgtype").equals("exit")){
                    clientSocket.close();
                    udpSocket.close();
                    return null;
                }
                handleMessage(msgtype , msg) ;
            }
        }  // Error handling
        catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private void handleMessage(String msgtype , JsonObject msg){
        switch(msgtype){
            case "register":
                handleRegister(msg);
                break;
                    case "updateCredentials":
                        handleUpdateCredentials(msg);
                        break;
                    case "login":
                        handleLogin(msg);
                        break;
                    case "logout":
                        handleLogout(msg);
                        break;
                    case "limitOrder":
                        handleLimitOrder(msg);
                        break;
                    case "marketOrder":
                        handleMarketOrder(msg);
                        break;
                    case "stopOrder":
                        handleStopOrder(msg);
                        break;
                    case "cancelOrder":
                        handleCancelOrder(msg);
                        break;
                    case "getPriceHistory":
                        handleGetPriceHistory(msg);
                        break;
        }
    }

    private void handleRegister (JsonObject msg){
        //creo l'oggetto risposta
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "register");

        String name = msg.get("username").getAsString();
        if(ServerMain.clientsUsernames.containsKey(name)){
            //caso in cui lo username e' gia' usato
            response.addProperty("response", "102");
            response.addProperty("errorMessage", "username already exists");
        } else {
            //caso in cui lo username e' disponibile
            response.addProperty("response", "100");
            response.addProperty("errorMessage", "OK");

            //Aggiungo l'utente registrato alla hashmap degli utenti
            ServerMain.clientsUsernames.put(name,new User(name, msg.get("password").getAsString(), clientSocket));

            //Quando aggiungo uno user nuovo vado a cambiare il file credenziali
            ServerMain.saveCredentialsToFile();
        }
        //mando il messaggio composto al client
        System.out.println("Sending " + response.toString() + " to client " + clientSocket);
        this.out.println(response.toString());
    }

    private void handleUpdateCredentials(JsonObject msg){
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "updateCredentials");
        String name = msg.get("username").getAsString();
        String old_pswd = msg.get("currentPassword").getAsString();
        String new_pswd = msg.get("newPassword").getAsString();
        //username/psw mismatch o nessuno user trovato
        if(!ServerMain.clientsUsernames.containsKey(name) || !old_pswd.equals(ServerMain.clientsUsernames.get(name).password)){
            response.addProperty("response", "102");
            response.addProperty("errorMessage", "username/old_pswd mismatch o non existent username");
        } else if (new_pswd.equals(ServerMain.clientsUsernames.get(name).password)) {
            response.addProperty("response", "103");
            response.addProperty("errorMessage", "new password has to be new");
        } else if (ServerMain.clientsUsernames.get(name).logged){
            response.addProperty("response", "104");
            response.addProperty("errorMessage", "user is logged in, log off");
        }else{
            try{
                ServerMain.clientsUsernames.get(name).password = new_pswd;
                response.addProperty("response", "100");
                response.addProperty("errorMessage", "OK");

                //Quando cambio una password aggiorno il file credenziali
                ServerMain.saveCredentialsToFile();
            }
            catch(Exception e){
                response.addProperty("response", "105");
                response.addProperty("errorMessage", "Unknown Error");
            }
        }
        //Mando il messaggio composto
        this.out.println(response.toString());
    }

    private void handleLogin (JsonObject msg){
        JsonObject response = new JsonObject();
        msg.addProperty("msgtype", "login");
        //Prendo i dati
        String name = msg.get("username").getAsString();
        String password = msg.get("password").getAsString();

        //Caso username/psw mismatch o no logged user
        if(!ServerMain.clientsUsernames.containsKey(name) || !password.equals(ServerMain.clientsUsernames.get(name).password)){
            response.addProperty("response", "102");
            response.addProperty("errorMessage", "username/password mismatch o non existent username");
        }else if(ServerMain.clientsUsernames.get(name).logged){
            //Utente gia' loggato
            response.addProperty("response", "103");
            response.addProperty("errorMessage", "user is logged in, log off");
        }else{
            try{
                //provo ad associare lo user al thread e segno che e' loggato
                ServerMain.clientsUsernames.get(name).logged = true;
                loggedUser = ServerMain.clientsUsernames.get(name) ;
                loggedUser.socket = clientSocket ;
                loggedUser.udpPort = udpPort ;
                System.out.println(loggedUser.username + " logged in on socket" + loggedUser.socket);
                System.out.println(loggedUser.username + " logged in on udpPort" + loggedUser.udpPort);
                response.addProperty("response", "100");
                response.addProperty("errorMessage", "OK");
            }
            catch (Exception e) {
                //se ho qualche errore non conosciuto mando 105
                response.addProperty("response", "105");
                response.addProperty("errorMessage", "Unknown Error");
            }
        }
        //Mando il messaggio composto
        this.out.println(response.toString());
    }

    private void handleLogout (JsonObject msg){
        JsonObject response = new JsonObject();
        if(loggedUser != null){
            ServerMain.clientsUsernames.get(loggedUser.username).logged = false;
            loggedUser = null;
            response.addProperty("response", "100");
            response.addProperty("errorMessage", "OK");
        } else {
            response.addProperty("response", "101");
            response.addProperty("errorMessage", "Error");
        }
        //Mando il messaggio composto
        this.out.println(response.toString());
    }

    private void handleLimitOrder (JsonObject msg){
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "limitOrder");
        if(loggedUser != null){
            System.out.println("Testing handleLimitOrder");
            //create new order from msg
            Boolean type = msg.get("type").getAsString().equals("ask") ;
            Integer size = msg.get("size").getAsInt();
            Integer price = msg.get("price").getAsInt();
            Order.LimitOrder newOrder = new Order.LimitOrder(loggedUser.username , type , size, price) ;

            //provo a vedere se posso eseguirlo subito
            if(type){
                if(!ServerMain.orderBook.bidOrders.isEmpty() && ServerMain.orderBook.bidOrders.getFirst().price <= price){
                    if(stopOrdersHandler.tryMarketOrder(newOrder)){
                        response.addProperty("orderID", newOrder.timestamp);

                        //Notify order sender
                        ServerMain.notifyUser(loggedUser.username , ServerMain.createUdpMessage(
                                System.currentTimeMillis(),
                                "limitOrder",
                                "ask",
                                size,
                                price,
                                System.currentTimeMillis()
                        ));

                        this.out.println(response.toString());
                        return ;
                    }
                }
            }else{
                if(!ServerMain.orderBook.askOrders.isEmpty() && ServerMain.orderBook.askOrders.getFirst().price >= price){
                    if(stopOrdersHandler.tryMarketOrder(newOrder)){
                        response.addProperty("orderID", newOrder.timestamp);

                        //Notify order sender
                        ServerMain.notifyUser(loggedUser.username , ServerMain.createUdpMessage(
                                System.currentTimeMillis(),
                                "limitOrder",
                                "bid",
                                size,
                                price,
                                System.currentTimeMillis()
                        ));

                        this.out.println(response.toString());
                        return ;
                    }
                }
            }


            //Se non lo posso eseguire subito lo aggiungo agli ordini in attesa
            if(type){
                //tipo == 1 -> ask order
                synchronized (ServerMain.orderBookLock){
                    ServerMain.orderBook.addOrder(ServerMain.orderBook.askOrders , newOrder);
                }
            } else {
                //tipo == 0 -> bid order
                synchronized (ServerMain.orderBookLock) {
                    ServerMain.orderBook.addOrder(ServerMain.orderBook.bidOrders, newOrder);
                }
            }
            // order timestamp = id
            response.addProperty("orderID", newOrder.timestamp);

            //Debug
            System.out.println("AskBook");
            ServerMain.orderBook.printOrders(ServerMain.orderBook.askOrders);
            System.out.println("BidBook");
            ServerMain.orderBook.printOrders(ServerMain.orderBook.bidOrders);
        }else{
            response.addProperty("orderID", "-1 - not logged in");
        }
        this.out.println(response.toString());
    }

    //True = order OK , False = order rejected
    public Boolean handleMarketOrder (JsonObject msg){
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "marketOrder");
        Integer orderSize = msg.get("size").getAsInt();
        if(loggedUser == null){
            response.addProperty("orderID", "-1 - not logged in");
            this.out.println(response.toString());
            return false;
        }

        OrderBook.OrderBookEntry entry;


        try {
            if (msg.get("type").getAsString().equals("ask")) {
                entry = ServerMain.orderBook.bidOrders.getFirst();
            } else {
                entry = ServerMain.orderBook.askOrders.getFirst();
            }
        }catch(NoSuchElementException e){
            System.out.println("Received an order for a type with no entries");
            response.addProperty("orderID", "-1 - cannot satisfy order");
            this.out.println(response.toString());
            return false;
        }
        Integer prezzoUsato = 0 ;
        if(entry != null){
            prezzoUsato = entry.price ;
        }else{
            response.addProperty("orderID", "-1 - cannot satisfy order");
            this.out.println(response.toString());
            return false;
        }
        //se non posso soddisfare l'ordine errore
        if(entry.size < orderSize){
            response.addProperty("orderID", "-1 - size too big cannot satisfy order");
            this.out.println(response.toString());
            return false;
        }else{
            //ordino gli ordini associati a quel prezzo per timestamp decrescente
            ArrayList<Order.LimitOrder> ordersUsed = new ArrayList<>() ;
            entry.orders.sort(Comparator.comparing((Order.LimitOrder o) -> o.timestamp)) ;
            for(Order.LimitOrder order : entry.orders){
                if(!order.username.equals(loggedUser.username)){
                    ordersUsed.add(order);
                    if(orderSize > order.size){
                        entry.size -= order.size;
                        orderSize -= order.size;
                        entry.orders.remove(order);
                    }else{
                        entry.size -= orderSize;
                        order.size -= orderSize;
                        orderSize = 0 ;

                        //remove entry from orderBook
                        if(msg.get("type").getAsString().equals("ask")){
                            ServerMain.orderBook.bidOrders.remove(entry);
                        }else{
                            ServerMain.orderBook.askOrders.remove(entry);
                        }
                        break ;
                    }
                }
            }
            response.addProperty("orderId" , String.valueOf(System.currentTimeMillis()));
            System.out.println("[DEBUG] orders coinvolti : " + ordersUsed);
            //save trans
            for(Order.LimitOrder order : ordersUsed){
                ServerMain.appendToStoricoTransazioni(order.timestamp , msg.get("type").getAsString() , msg.get("msgtype").getAsString() , order.size , order.limitPrice * order.size , order.timestamp);
            }
            ServerMain.saveStoricoTransazioni();

            //notify
            //marketOrder
            ServerMain.notifyUser(loggedUser.username , ServerMain.createUdpMessage(
                            System.currentTimeMillis(),
                            "ask",
                            "marketOrder",
                            msg.get("size").getAsInt(),
                            prezzoUsato,
                            System.currentTimeMillis()
                             ));

            //limitOrder(s)
            for(Order.LimitOrder order : ordersUsed){
                ServerMain.notifyUser(order.username , ServerMain.createUdpMessage(
                        order.timestamp,
                        "ask",
                        "limitOrder",
                        order.size,
                        prezzoUsato,
                        System.currentTimeMillis()
                ));
            }
        }
        this.out.println(response.toString());
        return true;
    }

    private void handleStopOrder (JsonObject msg){
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "stopOrder");
        if(loggedUser == null){
            response.addProperty("orderID", "-1 - not logged in");
            this.out.println(response.toString());
            return;
        }else{
            Boolean type = msg.get("type").getAsString().equals("ask") ;
            Integer size = msg.get("size").getAsInt();
            Integer price = msg.get("stopPrice").getAsInt();
            Order.StopOrder stopOrder = new Order.StopOrder(loggedUser.username , type , size , price);
            synchronized (ServerMain.stopOrdersLock){
                ServerMain.orderBook.stopOrders.add(stopOrder) ;
            }
            System.out.println("Added stopOrder to list - " + stopOrder + "\n" + ServerMain.orderBook.stopOrders);
            response.addProperty("orderID",stopOrder.timestamp);
            this.out.println(response.toString());
        }
    }

    private void handleCancelOrder (JsonObject msg){
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "cancelOrder");
        if(loggedUser == null){
            response.addProperty("101", "not logged in");
        }else{
            long targetId = msg.get("orderId").getAsLong();
            //cerco l'ordine da cancellare
            //cerco in askOrders
            for (OrderBook.OrderBookEntry entry : ServerMain.orderBook.askOrders) {
                for (Order.LimitOrder order : entry.orders) {
                    if (order.timestamp == targetId) {
                        if(order.username.equals(loggedUser.username)){
                            //update entry + remove order
                            entry.size -= order.size;
                            entry.price = entry.size * entry.size ;
                            entry.orders.remove(order);
                            response.addProperty("orderID", "100");
                            this.out.println(response.toString());
                            return ;
                        }else{
                            response.addProperty("orderID", "101 - order belongs to a diff user");
                            this.out.println(response.toString());
                            return ;
                        }

                    }
                }
            }

            // Cerca nei livelli di prezzo in `bidOrders`
            for (OrderBook.OrderBookEntry entry : ServerMain.orderBook.bidOrders) {
                for (Order.LimitOrder order : entry.orders) {
                    if (order.timestamp == targetId) {
                        if(order.username.equals(loggedUser.username)){
                            //update entry + remove order
                            entry.size -= order.size;
                            entry.price = entry.size * entry.size ;
                            entry.orders.remove(order);
                            response.addProperty("orderID", "100");
                            this.out.println(response.toString());
                            return ;
                        }else{
                            response.addProperty("orderID", "101 - order belongs to a diff user");
                            this.out.println(response.toString());
                            return ;
                        }
                    }
                }
            }

            // Cerca negli ordini "StopOrder"
            for (Order.StopOrder stopOrder : ServerMain.orderBook.stopOrders) {
                if (stopOrder.timestamp == targetId) {
                    if(stopOrder.username.equals(loggedUser.username)){
                        synchronized (ServerMain.stopOrdersLock){
                            ServerMain.orderBook.stopOrders.remove(stopOrder);
                        }
                        response.addProperty("orderID", "100");
                        this.out.println(response.toString());
                        return ;
                    }else{
                        response.addProperty("orderID", "101 - order belongs to a diff user");
                        this.out.println(response.toString());
                        return ;
                    }
                }
            }

        }
    }

    private void handleGetPriceHistory (JsonObject msg){
        Integer month ;
        Integer year ; 
    try{
	        month = Integer.parseInt(msg.get("month").getAsString().substring(0,2));
        	year = Integer.parseInt(msg.get("month").getAsString().substring(2,6));
	}catch(Exception e){
		return;
	}
        System.out.println("Processing month/year:  "+ month + "-" + year);

        // Configurazione del formattatore di date e calcolo dei limiti del mese
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        calendar.set(year, month - 1, 1, 0, 0, 0); // Inizio mese
        long startTimestamp = calendar.getTimeInMillis() / 1000;

        calendar.add(Calendar.MONTH, 1); // Inizio mese successivo
        long endTimestamp = (calendar.getTimeInMillis() - 1) / 1000; // Fine mese corrente

        System.out.println("start time: " + dateFormat.format(startTimestamp*1000) + " end time: " + dateFormat.format(endTimestamp*1000));


        // Inizializza la risposta
        JsonObject response = new JsonObject();
        response.addProperty("msgtype", "getPriceHistory");

        //Carico le entries dallo storico
        List<Order.EntryStorico> entryList = new ArrayList<>();
        for(JsonElement element : ServerMain.storicoTransazioni){
            JsonObject obj = element.getAsJsonObject();
            int price = obj.get("price").getAsInt();
            long timestamp = obj.get("timestamp").getAsLong();
            entryList.add(new Order.EntryStorico(price , timestamp));
        }

        System.out.println("start time: " + startTimestamp + " end time: " + endTimestamp + " first order timestamp:" + entryList.getFirst().timestamp);

        System.out.println("EntryList before filter, size:"+entryList.size());

        // Filtra gli entry per il mese specificato
        entryList = entryList.stream()
                .filter(e -> e.timestamp >= startTimestamp && e.timestamp <= endTimestamp)
                .collect(Collectors.toList());
        System.out.println("EntryList after filter, size:"+entryList.size());

        // Raggruppa gli entry per giorno
        Map<String, List<Order.EntryStorico>> groupedByDay = entryList.stream()
                .collect(Collectors.groupingBy(e -> dateFormat.format(new Date(e.timestamp*1000))));

        System.out.println("Grouped By Day : " + groupedByDay);

        //Preparo il messaggio
        JsonObject result = new JsonObject();
        result.addProperty("msgtype", "getPriceHistory");

        //Controllo che ci sia almeno un ordine, se empty return error
        if(groupedByDay.isEmpty()){
            result.addProperty("errorMsg" , "no trades in given month");
            this.out.println(result.toString());
            return;
        }


        // Calcola i dati per ogni giorno
        for (Map.Entry<String, List<Order.EntryStorico>> dayEntry : groupedByDay.entrySet()) {
            String day = dayEntry.getKey();
            List<Order.EntryStorico> dayEntries = dayEntry.getValue();

            int openPrice = dayEntries.getFirst().price;
            int closePrice = dayEntries.get(dayEntries.size() - 1).price;
            int maxPrice = dayEntries.stream().mapToInt(e -> e.price).max().orElse(0);
            int minPrice = dayEntries.stream().mapToInt(e -> e.price).min().orElse(0);

            JsonObject dayStats = new JsonObject();
            dayStats.addProperty("open", openPrice);
            dayStats.addProperty("close", closePrice);
            dayStats.addProperty("high", maxPrice);
            dayStats.addProperty("low", minPrice);

            System.out.println("Calcolated Result:" + dayStats);

            // Aggiungi i dati calcolati alla risposta
            result.add(day, dayStats);
        }

        this.out.println(result.toString());
    }


}

class stopOrdersHandler implements Runnable{
    public void run(){
        System.out.println("stopOrdersHandler started");
        while(true) {
            //check se ci sono stop orders
            if(ServerMain.orderBook.stopOrders != null && !ServerMain.orderBook.stopOrders.isEmpty()){
            //sort per ordine piu' vecchio
                ServerMain.orderBook.stopOrders.sort(Comparator.comparingLong((Order.StopOrder o) -> o.timestamp).reversed());
                for(Order.StopOrder stopOrder : ServerMain.orderBook.stopOrders){
                    //ask
                    if(stopOrder.ask){
                        if(!ServerMain.orderBook.bidOrders.isEmpty() && stopOrder.stopPrice <= ServerMain.orderBook.bidOrders.getFirst().price){
                            Integer orderSize = stopOrder.size ;
                            Integer price = ServerMain.orderBook.bidOrders.getFirst().price;
                            if(tryMarketOrder(stopOrder)){//if order goes throgh remove it from list
                                System.out.println("stopOrder " + stopOrder + " was accepted");

                                //creazione e invio udpmsg
                                ServerMain.notifyUser(stopOrder.username , ServerMain.createUdpMessage(
                                        stopOrder.timestamp,
                                        "ask",
                                        "stopOrder",
                                        orderSize,
                                        price,
                                        System.currentTimeMillis()
                                ));
                                ServerMain.orderBook.stopOrders.remove(stopOrder);
                                break;
                            }
                        }
                    }else{
                    //bid
                        if(!ServerMain.orderBook.askOrders.isEmpty() && stopOrder.stopPrice <= ServerMain.orderBook.askOrders.getFirst().price){
                            Integer orderSize = stopOrder.size ;
                            Integer price = 0 ;
                            if(!ServerMain.orderBook.bidOrders.isEmpty()) {
                                price = ServerMain.orderBook.bidOrders.getFirst().price;
                                if (tryMarketOrder(stopOrder)) { //if order goes throgh remove it from list
                                    System.out.println("stopOrder " + stopOrder + " was accepted");
                                    //creazione e invio udpmsg
                                    ServerMain.notifyUser(stopOrder.username, ServerMain.createUdpMessage(
                                            stopOrder.timestamp,
                                            "bid",
                                            "stopOrder",
                                            orderSize,
                                            price,
                                            System.currentTimeMillis()
                                    ));
                                    ServerMain.orderBook.stopOrders.remove(stopOrder);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            //wait 10 seconds
            try {
                // Sleep for 10 seconds (10,000 milliseconds)
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                System.err.println("Thread was interrupted!");
            }
        }
    }

    public static Boolean tryMarketOrder(Order stopOrder){
        System.out.println("tryMarketOrder started");
        OrderBook.OrderBookEntry entry ;
        try {
            if (stopOrder.ask) {
                entry = ServerMain.orderBook.bidOrders.getFirst();
            } else {
                entry = ServerMain.orderBook.askOrders.getFirst();
            }
        }catch(NoSuchElementException e){
            return false;
        }
        Integer prezzoUsato = 0 ;
        if(entry != null){
            prezzoUsato = entry.price;
        }

        //se non posso soddisfare l'ordine errore
        if(entry.size < stopOrder.size){
//            response.addProperty("orderID", "-1 - size too big cannot satisfy order");
//            this.out.println(response.toString());
            return false;
        }else{
            //ordino gli ordini associati a quel prezzo per timestamp descrescente
            ArrayList<Order.LimitOrder> ordersUsed = new ArrayList<>() ;
            entry.orders.sort(Comparator.comparing((Order.LimitOrder o) -> o.timestamp)) ;
            for(Order.LimitOrder order : entry.orders){
                if(!order.username.equals(stopOrder.username)){
                    ordersUsed.add(order);
                    if(stopOrder.size > order.size){
                        stopOrder.size -= order.size;
                        entry.orders.remove(order);
                    }else{
                        order.size -= stopOrder.size;
                        stopOrder.size = 0 ;
                        //delete empty entry
                        if(entry.size == 0){
                            if(stopOrder.ask){
                                ServerMain.orderBook.bidOrders.remove(entry);
                            }else{
                                ServerMain.orderBook.askOrders.remove(entry);
                            }
                        }
                        break ;
                    }
                }
            }
//            response.addProperty("orderId" , String.valueOf(System.currentTimeMillis()));
            System.out.println("[DEBUG] orders coinvolti : " + ordersUsed);
            //todo save transazione
            for(Order.LimitOrder order : ordersUsed){
                ServerMain.appendToStoricoTransazioni(stopOrder.timestamp, stopOrder.ask ? "ask" : "bid" , "stopOrder" , stopOrder.size , entry.price * stopOrder.size, stopOrder.timestamp);
            }
            ServerMain.saveStoricoTransazioni();

            //notify users coinvolti
            JsonObject udpmsg = new JsonObject();
            for(Order.LimitOrder order : ordersUsed){
                ServerMain.notifyUser(order.username , ServerMain.createUdpMessage(
                        order.timestamp,
                        "ask",
                        "limitOrder",
                        order.size,
                        prezzoUsato,
                        System.currentTimeMillis()
                ));
            }
        }
        return true;
    }
}