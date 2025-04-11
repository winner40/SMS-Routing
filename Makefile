# Compiler et classpath
JAVAC = javac
JAVA = java
CP=./:./lib/amqp-client-5.16.0.jar:./lib/slf4j-api-1.7.36.jar:./lib/slf4j-simple-1.7.36.jar

# Fichiers sources
SRC = ./SMS/Antenna.java ./SMS/User.java

# Cible par défaut
all: compile

# Compilation
compile:
	$(JAVAC) -cp $(CP) $(SRC)

# Exécution du programme Antenna
run-antenna:
	$(JAVA) -cp $(CP) SMS.Antenna $(ARGS)

# Exécution du programme User
run-user:
	$(JAVA) -cp $(CP) SMS.User $(ARGS)

# Nettoyage
clean:
	rm -f *.class
	rm -f SMS/*.class

# Allow passing arguments directly
%:
	@:
