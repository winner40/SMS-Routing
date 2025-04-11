package SMS;

import com.rabbitmq.client.*;
import java.util.*;

public class Antenna {
    private static final String SMS_EXCHANGE = "sms_exchange";
    private static final String CONTROL_EXCHANGE = "control_exchange";
    private static final Map<String, AntennaInfo> ANTENNAS = new HashMap<>();
    private static final String[] ANTENNA_IDS = {"A", "B", "D", "E"}; 
    private static final int[][] ADJACENCY_MATRIX; 

    static {
        ANTENNAS.put("A", new AntennaInfo("A", 0, 0, 50)); 
        ANTENNAS.put("B", new AntennaInfo("B", 10, 10, 50));
        ANTENNAS.put("D", new AntennaInfo("D", 5, 15, 50));
        ANTENNAS.put("E", new AntennaInfo("E", 20, 20, 50));

        ADJACENCY_MATRIX = new int[][] {
            {0, 1, 1, 0}, 
            {1, 0, 1, 1}, 
            {1, 1, 0, 0}, 
            {0, 1, 0, 0}  
        };
    }

    private static Map<String, String> userLocations = new HashMap<>();
    private String antennaId;
    private Channel channel;
    private String smsQueue;
    private String controlQueue;
    private Set<String> processedMessages = new HashSet<>();

    static class AntennaInfo {
        String id;
        double x, y, radius;

        AntennaInfo(String id, double x, double y, double radius) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.radius = radius;
        }
    }

    public Antenna(String antennaId) throws Exception {
        this.antennaId = antennaId;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        this.channel = connection.createChannel();

        channel.exchangeDeclare(SMS_EXCHANGE, "topic");
        channel.exchangeDeclare(CONTROL_EXCHANGE, "topic");

        this.smsQueue = channel.queueDeclare().getQueue();
        this.controlQueue = channel.queueDeclare().getQueue();

        channel.queueBind(smsQueue, SMS_EXCHANGE, antennaId + ".*");
        channel.queueBind(controlQueue, CONTROL_EXCHANGE, antennaId + ".*");

        System.out.println(" [*] Antenna " + antennaId + " started");
        startAntenna();
    }

    private void startAntenna() throws Exception {

        // Rule 3: Receiving and delivering an SMS
        DeliverCallback smsCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String[] parts = routingKey.split("\\.");
            String type = parts[1];
            String dest = parts[2];

            System.out.println(" [SMS] Received on " + antennaId + ": " + routingKey + " -> " + message);

            if (type.equals("SEND_SMS")) {
                if (userLocations.containsKey(dest) && userLocations.get(dest).equals(antennaId)) {
                    // Case where sender and recipient are on the same antenna
                    channel.basicPublish(SMS_EXCHANGE, dest + ".RECEIVE", null, message.getBytes("UTF-8"));
                    System.out.println(" [x] Delivered directly to " + dest + ": " + message);
                } else {
                    try {
                        broadcastToNeighbors("sms", "SEND_SMS", message, dest, antennaId);
                    } catch (Exception e) {
                        System.err.println(" [!] Error broadcasting SEND_SMS: " + e.getMessage());
                    }
                }
            }
        };

        // Rules 1, 2, and 4: Handling control messages
        DeliverCallback controlCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            String routingKey = delivery.getEnvelope().getRoutingKey();
            String[] parts = routingKey.split("\\.");
            String type = parts[1];
            String[] messageParts = message.split(";", 3);
            String dest = messageParts[0];
            String origin = messageParts[1];
            String content = messageParts.length > 2 ? messageParts[2] : "";

            String messageKey = dest + ";" + origin + ";" + content;
            if (processedMessages.contains(messageKey)) return;
            processedMessages.add(messageKey);

            System.out.println(" [CTRL] Received on " + antennaId + ": " + routingKey + " -> " + message);

        // Rule 1: Receiving a search request
            if (type.equals("FIND")) {
                System.out.println(" [ASK] Processing FIND for user " + dest + " from " + origin);
                if (userLocations.containsKey(dest) && userLocations.get(dest).equals(antennaId)) {
                    channel.basicPublish(CONTROL_EXCHANGE, origin + ".FOUND", null, (dest + ";" + antennaId + ";" + content).getBytes("UTF-8"));
                    System.out.println(" [ACK] Found " + dest + ", sent FOUND to " + origin);
                } else {
                    try {
                        broadcastToNeighborsExcept("control", "FIND", message, dest, origin);
                    } catch (Exception e) {
                        System.err.println(" [!] Error broadcasting FIND: " + e.getMessage());
                    }
                }

            // Rule 2: Receiving a FOUND response
            } else if (type.equals("FOUND")) {
                System.out.println(" [ACK] Processing FOUND from " + messageParts[1] + " for " + dest);
                String foundAntenna = messageParts[1];
                if (antennaId.equals(origin)) {
                    channel.basicPublish(SMS_EXCHANGE, foundAntenna + ".SEND_SMS." + dest, null, content.getBytes("UTF-8"));
                    System.out.println(" [x] SMS routed to " + foundAntenna + " for " + dest);
                } else {
                    channel.basicPublish(CONTROL_EXCHANGE, origin + ".FOUND", null, message.getBytes("UTF-8"));
                    System.out.println(" [x] Relayed FOUND to " + origin);
                }

            // Rule 4: Handling mobility
            } else if (type.equals("MOVE")) {
                String user = messageParts[0];
                String newAntennaId = messageParts[1];
                if (userLocations.containsKey(user) && userLocations.get(user).equals(antennaId)) {
                    userLocations.remove(user);
                    System.out.println(" [MOVE] User " + user + " left " + antennaId);
                }
                if (antennaId.equals(newAntennaId)) {
                    userLocations.put(user, antennaId);
                    System.out.println(" [MOVE] User " + user + " connected to " + antennaId);
                }
            } 
        };

        channel.basicConsume(smsQueue, true, smsCallback, consumerTag -> {});
        channel.basicConsume(controlQueue, true, controlCallback, consumerTag -> {});
    }

   // Utility function for broadcasting to neighbors
    private void broadcastToNeighbors(String type, String action, String message, String dest, String origin) throws Exception {
        int myIndex = Arrays.asList(ANTENNA_IDS).indexOf(antennaId);
        for (int i = 0; i < ANTENNA_IDS.length; i++) {
            if (ADJACENCY_MATRIX[myIndex][i] == 1) {
                String neighbor = ANTENNA_IDS[i];
                channel.basicPublish(type.equals("control") ? CONTROL_EXCHANGE : SMS_EXCHANGE,
                        neighbor + "." + action, null, message.getBytes("UTF-8"));
                System.out.println(" [x] Broadcast " + action + " to " + neighbor);
            }
        }
    }

     // Utility function for broadcasting to neighbors except the origin
    private void broadcastToNeighborsExcept(String type, String action, String message, String dest, String origin) throws Exception {
        int myIndex = Arrays.asList(ANTENNA_IDS).indexOf(antennaId);
        for (int i = 0; i < ANTENNA_IDS.length; i++) {
            if (ADJACENCY_MATRIX[myIndex][i] == 1 && !ANTENNA_IDS[i].equals(origin)) {
                String neighbor = ANTENNA_IDS[i];
                channel.basicPublish(CONTROL_EXCHANGE, neighbor + "." + action, null, message.getBytes("UTF-8"));
                System.out.println(" [x] Broadcast " + action + " to " + neighbor + " (except " + origin + ")");
            }
        }
    }

        // Utility function for assigning a user to an antenna
    public static String assignUser(double userX, double userY, String userId) {
        String closestAntenna = null;
        double minDistance = Double.MAX_VALUE;

        for (AntennaInfo antenna : ANTENNAS.values()) {
            double distance = Math.sqrt(Math.pow(userX - antenna.x, 2) + Math.pow(userY - antenna.y, 2));
            if (distance <= antenna.radius && distance < minDistance) {
                minDistance = distance;
                closestAntenna = antenna.id;
            }
        }

        if (closestAntenna != null) {
            userLocations.put(userId, closestAntenna);
            return closestAntenna;
        }
        return "No coverage";
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: Antenna <antenna_id>");
            System.exit(1);
        }
        new Antenna(args[0]);
    }
}