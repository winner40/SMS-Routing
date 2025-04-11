# SMS_ROUTING
=======
# Routing System

## Description

This project implements a message routing system for SMS between antennas and users using RabbitMQ. It includes the management of antennas, users, and the messages exchanged between them.

## Project Structure

- **SMS/Antenna.java**: Manages antennas, their communication, and message routing.
- **SMS/User.java**: Represents a user who can send and receive messages.
- **RabbitMQ**: Used as the messaging system for communication between antennas and users.

## Prerequisites

- Java JDK 8 or higher
- RabbitMQ installed and configured
- Make (to automate compilation and execution)

## Compilation

To compile the project, run the following command in the project directory:

```sh
make compile
```

This command will compile all Java files and generate the necessary `.class` files.

## Execution

### Start RabbitMQ

Ensure RabbitMQ is running before starting the server or clients. You can start RabbitMQ with the following command (depending on your system):

```sh
sudo systemctl start rabbitmq-server
```

### Start an Antenna

To start an antenna, run the following command:

```sh
make run-antenna ARGS="<antenna_id>"
```

Replace `<antenna_id>` with the identifier of the antenna (e.g., `A`, `B`, `D`, or `E`).

### Start a User

To start a user, run the following command:

```sh
make run-user ARGS="<user_id> <x> <y>"
```

Replace `<user_id>` with the user's identifier, and `<x>` and `<y>` with their initial coordinates.

## Features

- **Antenna**:
  - Manages user connections.
  - Routes messages between users connected to different antennas.
  - Broadcasts control messages to locate users.

- **User**:
  - Can send messages to other users.
  - Can receive messages
  - Can move and connect to a new antenna.

## Cleaning Up

To remove compiled files, run the following command:

```sh
make clean
```

## Authors

- Girncuti Dany Carol (dany-carol.girincuti@etu.univ-grenoble-alpes.fr)
- Dagar Tiyo Vaneck Duramel (vaneck-duramel.dagar-tiyo@etu.univ-grenoble-alpes.fr)
- Mert Ozkarsiyakali
