//todo thread per gestione messaggi async udp per le vendite

import Settings.ClientSettings;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientMain {
    public static void main(String[] args) {
        String host = Settings.ClientSettings.HOSTNAME;
        int port = Settings.ClientSettings.TCPPORT;

        Socket socket;
        BufferedReader in;
        PrintWriter out;
        Scanner scanner;
        DatagramSocket udpSocket = null ;

        // Initialize the structures for the connection and input
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            System.out.println("Connected to server: " + host + ":" + port);
            scanner = new Scanner(System.in);  // Single Scanner instance for the whole program

            // Inizializza il socket UDP per la ricezione, lascio scegliere a java la porta
            udpSocket = new DatagramSocket();
            System.out.println("Listening for UDP messages on port: " + udpSocket.getLocalPort());
            // Comunica la porta UDP al server tramite TCP
            out.println(udpSocket.getLocalPort());

            // Avvia il thread per la gestione dei messaggi UDP
            DatagramSocket finalUdpSocket = udpSocket;
            Thread udpListenerThread = new Thread(() -> listenForUdpMessages(finalUdpSocket));
            udpListenerThread.start();

        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // Enter the message sending and reading loop
        try {
            String msgType = "";
            while (true) {
                JsonObject msg = new JsonObject();
                System.out.print("Enter a message (or 'exit' to terminate): ");
                msgType = scanner.nextLine();  // This line is blocking and waits for input

                // Generate different messages based on the type -> 1 if correct
                if(createMessage(msgType , msg , scanner)){
                    // Send the message to the server
                    out.println(msg.toString());

                    // Read the server's response
                    String serverResponse = in.readLine();
                    System.out.println("Server response: " + serverResponse);
                }
                // Terminate if the user types "exit"
                if (msgType.equalsIgnoreCase("exit")) {
                    socket.close();
                    udpSocket.close();
                    System.exit(0);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            scanner.close();  // Close the Scanner when the program exits
        }
    }

    private static void listenForUdpMessages(DatagramSocket udpSocket) {
        byte[] buffer = new byte[1024]; // Buffer per i dati UDP
        try {
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet); // Riceve il pacchetto
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("UDP Message Received: " + message);
                System.out.print("Enter a message (or 'exit' to terminate): ");

            }
        } catch (IOException e) {
            if (!udpSocket.isClosed()) {
                System.err.println("Error in UDP listener: " + e.getMessage());
            }
        }
    }

    private static Boolean createMessage(String msgType , JsonObject msg , Scanner scanner) {
        switch (msgType) {
            case "register":
                sendRegister(msg, scanner);
                return true;
            case "updateCredentials":
                sendUpdateCredentials(msg, scanner);
                return true;
            case "login":
                sendLogin(msg, scanner);
                return true;
            case "logout":
                sendLogout(msg, scanner);
                return true;
            case "limitOrder":
                sendLimitOrder(msg, scanner);
                return true;
            case "marketOrder":
                sendMarketOrder(msg, scanner);
                return true;
            case "stopOrder":
                sendStopOrder(msg, scanner);
                return true;
            case "cancelOrder":
                sendCancelOrder(msg, scanner);
                return true;
            case "getPriceHistory":
                sendGetPriceHistory(msg, scanner);
                return true;
            case "exit":
                    msg.addProperty("msgtype" , "exit");
                    return true;
            default:
                System.out.println("Unknown message msgtype.");
                return false;
        }
    }

    private static void sendRegister(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "register");
        System.out.print("Username: ");
        msg.addProperty("username", scanner.nextLine());
        System.out.print("Password: ");
        msg.addProperty("password", scanner.nextLine());
    }

    private static void sendUpdateCredentials(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "updateCredentials");
        System.out.print("Username: ");
        msg.addProperty("username", scanner.nextLine());
        System.out.print("Current Password: ");
        msg.addProperty("currentPassword", scanner.nextLine());
        System.out.print("New Password: ");
        msg.addProperty("newPassword", scanner.nextLine());
    }

    private static void sendLogin(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "login");
        System.out.print("---Login---\n");
        System.out.print("Username: ");
        msg.addProperty("username", scanner.nextLine());
        System.out.print("Password: ");
        msg.addProperty("password", scanner.nextLine());
    }

    private static void sendLogout(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "logout");
    }

    private static void sendLimitOrder(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "limitOrder");
        System.out.print("---Limit Order---\n");
        System.out.print("Order type: ");
        msg.addProperty("type", scanner.nextLine());
        System.out.print("Size: ");
        msg.addProperty("size", scanner.nextLine());
        System.out.print("Limit price: ");
        msg.addProperty("price", scanner.nextLine());
    }

    private static void sendMarketOrder(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "marketOrder");
        System.out.print("---Market Order---\n");
        System.out.print("Order type: ");
        msg.addProperty("type", scanner.nextLine());
        System.out.print("Size: ");
        msg.addProperty("size", scanner.nextLine());
    }

    private static void sendStopOrder(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "stopOrder");
        System.out.print("---Stop Order---\n");
        System.out.print("Order type: ");
        msg.addProperty("type", scanner.nextLine());
        System.out.print("Size: ");
        msg.addProperty("size", scanner.nextLine());
        System.out.print("Stop price: ");
        msg.addProperty("stopPrice", scanner.nextLine());
    }

    private static void sendCancelOrder(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "cancelOrder");
        System.out.print("---Cancel Order---\n");
        System.out.print("Order Id: ");
        msg.addProperty("orderId", scanner.nextLine());
    }

    private static void sendGetPriceHistory(JsonObject msg, Scanner scanner) {
        msg.addProperty("msgtype", "getPriceHistory");
        System.out.print("---Get Price History---\n");
        System.out.print("MonthYear [mmyyyy]: ");
        msg.addProperty("month", scanner.nextLine());
    }
}
