import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class OrderBook {

    TreeSet<OrderBookEntry> askOrders ;
    TreeSet<OrderBookEntry> bidOrders ;
    ArrayList<Order.StopOrder> stopOrders ;

    public OrderBook(){
        askOrders = new TreeSet<>(Comparator.comparingInt((OrderBookEntry o) -> o.price)) ;
        bidOrders = new TreeSet<>(Comparator.comparingInt((OrderBookEntry o) -> o.price).reversed()) ;
        stopOrders = new ArrayList<>() ;
    }

    public static class OrderBookEntry{
        Integer price;
        Integer size ;
        Integer total ;
        ArrayList<Order.LimitOrder> orders ;

        public OrderBookEntry(Integer price, Integer size){
            this.price = price;
            this.size = size;
            this.total  = price * size;
            this.orders = new ArrayList<>();
        }
    }

    //Metodo per aggiungere un ordine o aggiornare la size dell'ordine
    public void addOrder(TreeSet<OrderBookEntry> orders, Order.LimitOrder newOrder) {
        System.out.println("Adding " + newOrder.toString());
        // Cerca un ordine con lo stesso prezzo
        OrderBookEntry existingOrder = orders.stream()
                .filter(o -> o.price == newOrder.limitPrice)
                .findFirst()
                .orElse(null);

        if (existingOrder != null) {
            // Update size
            existingOrder.size += newOrder.size;
            // Update total
            existingOrder.total = existingOrder.size * existingOrder.price;
            // add to orders list
            existingOrder.orders.add(newOrder);
        } else {
            // Aggiungi il nuovo ordine
            OrderBookEntry temp = new OrderBookEntry(newOrder.limitPrice , newOrder.size);
            temp.orders.add(newOrder);
            orders.add(temp);

            System.out.println("Order " + newOrder.limitPrice + " added to " + orders.size() + " orders");
        }
    }

    //Metodo per stampare gli ordini
    public void printOrders(TreeSet<OrderBookEntry> orders) {
        if (orders.isEmpty()) {
            System.out.println("No orders available.");
            return;
        }

        for (OrderBookEntry entry : orders) {
            System.out.println("Price: " + entry.price + ", Size: " + entry.size + ", Total: " + entry.total);
            System.out.println("Orders: ");
            for (Order.LimitOrder order : entry.orders) {
                System.out.println("  - Order: LimitPrice=" + order.limitPrice + ", Size=" + order.size + " Type " + (order.ask ? "ask" : "bid") + " from "+order.username);
            }
        }
    }

}

