all:
	javac -Xlint:all *.java

pretty:
	prettier -w *.java
