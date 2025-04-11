package SMS;

import com.rabbitmq.client.*;
import java.util.Scanner;
import java.util.Random;

public class User {
    private static final String SMS_EXCHANGE = "sms_exchange"; 
    private static final String CONTROL_EXCHANGE = "control_exchange"; 
    private String userId;
    private double x, y; 
    private String currentAntenna; 
    private Channel channel;
    private String userQueue;

    public User(String userId, double x, double y) throws Exception {
        this.userId = userId;
        this.x = x;
        this.y = y;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        Connection connection = factory.newConnection();
        this.channel = connection.createChannel();


        channel.exchangeDeclare(SMS_EXCHANGE, "topic");
        channel.exchangeDeclare(CONTROL_EXCHANGE, "topic");


        this.userQueue = channel.queueDeclare().getQueue();
        this.currentAntenna = Antenna.assignUser(x, y, userId); 
        if (!currentAntenna.equals("No coverage")) {
            channel.queueBind(userQueue, SMS_EXCHANGE, currentAntenna + ".SEND_SMS." + userId);
            channel.basicPublish(CONTROL_EXCHANGE, currentAntenna + ".MOVE", null, (userId + ";" + currentAntenna).getBytes("UTF-8"));
            System.out.println(" [x] Connected to " + currentAntenna);
        }

        startMessageListener(); 
        runConsole(); 
    }

    private void startMessageListener() throws Exception {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");
            System.out.println(" [MSG] Received: " + message);
        };
        channel.basicConsume(userQueue, true, deliverCallback, consumerTag -> {});
    }

    private void runConsole() throws Exception {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== User " + userId + " at (" + x + "," + y + ") on " + currentAntenna + " ===");
            System.out.println("1. Send a message");
            System.out.println("2. Move to new coordinates");
            System.out.println("3. Simulate random movement");
            System.out.println("4. Exit");
            System.out.print("Choose an option: ");
            String choice = scanner.nextLine();

            if (choice.equals("1")) {
                System.out.print("Enter target user ID: ");
                String targetUser = scanner.nextLine();
                System.out.print("Enter message: ");
                String message = scanner.nextLine();
                sendMessage(targetUser, message);
            } else if (choice.equals("2")) {
                System.out.print("Enter new X: ");
                double newX = Double.parseDouble(scanner.nextLine());
                System.out.print("Enter new Y: ");
                double newY = Double.parseDouble(scanner.nextLine());
                move(newX, newY); 
            } else if (choice.equals("3")) {
                System.out.println(" [x] Exiting...");
                break;
            } else {
                System.out.println(" [x] Invalid option, try again.");
            }
        }
    }

    private void sendMessage(String targetUser, String message) throws Exception {
        if (currentAntenna.equals("No coverage")) {
            System.out.println(" [x] Cannot send message: No coverage");
            return;
        }
        String fullMessage = "Hello " + targetUser + ", this is " + userId + ": " + message;
        channel.basicPublish(CONTROL_EXCHANGE, currentAntenna + ".FIND", null, (targetUser + ";" + currentAntenna + ";" + fullMessage).getBytes("UTF-8"));
        System.out.println(" [x] Sent message to " + targetUser + " via " + currentAntenna);
    }

    private void move(double newX, double newY) throws Exception {
        String oldAntenna = currentAntenna;
        x = newX;
        y = newY;
        currentAntenna = Antenna.assignUser(newX, newY, userId);
        String moveMessage = userId + ";" + currentAntenna;
        if (!oldAntenna.equals("No coverage")) {
            channel.basicPublish(CONTROL_EXCHANGE, oldAntenna + ".MOVE", null, moveMessage.getBytes("UTF-8"));
        }
        if (!currentAntenna.equals("No coverage")) {
            channel.queueUnbind(userQueue, SMS_EXCHANGE, oldAntenna + ".SEND_SMS." + userId);
            channel.queueBind(userQueue, SMS_EXCHANGE, currentAntenna + ".SEND_SMS." + userId);
            channel.basicPublish(CONTROL_EXCHANGE, currentAntenna + ".MOVE", null, moveMessage.getBytes("UTF-8"));
        }
        System.out.println(" [x] Moved to (" + newX + "," + newY + ") on " + currentAntenna);
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: User <user_id> <x> <y>");
            System.exit(1);
        }
        new User(args[0], Double.parseDouble(args[1]), Double.parseDouble(args[2]));
    }
}