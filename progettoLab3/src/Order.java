import java.sql.Timestamp;

public class Order {
    String username ;
    Boolean ask ; // 1->ask , 0->bid
    Integer size ;
    long timestamp ;


    public Order(String username , Boolean ask, Integer size) {
        this.username = username;
        this.ask = ask;
        this.size = size;
        this.timestamp = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "Order{" +
                "ask=" + ask +
                ", size=" + size +
                ", username=" + username +
                '}';
    }

    public static class StopOrder extends Order {
        Integer stopPrice ;

        public StopOrder(String username, Boolean ask, Integer size, Integer stopPrice) {
            super(username , ask, size);
            this.stopPrice = stopPrice;
        }
    }

    public static class LimitOrder extends Order {
        Integer limitPrice ;
        public LimitOrder(String username , Boolean ask, Integer size, Integer limitPrice) {
            super(username , ask, size);
            this.limitPrice = limitPrice;
        }
    }

    public static class EntryStorico{
        Integer price ;
        long timestamp ;
        public EntryStorico(Integer price, long timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}
