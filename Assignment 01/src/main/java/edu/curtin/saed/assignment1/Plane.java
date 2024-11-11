package edu.curtin.saed.assignment1;

// Represents a plane in the air traffic simulation.
public class Plane {
    private int id;
    private double x;
    private double y;
    private double speed;
    private boolean inFlight;
    private Airport currentAirport;
    private Airport destination;

    // Constructs a new Plane object
    public Plane(int id, double x, double y, double speed, Airport currentAirport) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.inFlight = false; // Planes start on the ground
        this.currentAirport = currentAirport;
        this.destination = null;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    // Checks if the plane is currently in flight
    public boolean isInFlight() { 
        return inFlight;
    }

    // Sets whether the plane is currently in flight
    public void setInFlight(boolean inFlight) {
        this.inFlight = inFlight;
    }

    // Gets the airport where the plane is currently located
    public Airport getCurrentAirport() {
        return currentAirport;
    }

    // Sets the airport where the plane is currently located
    public void setCurrentAirport(Airport currentAirport) {
        this.currentAirport = currentAirport;
    }

    // Gets the destination airport of the plane
    public Airport getDestination() {
        return destination;
    }

    // Sets the destination airport of the plane
    public void setDestination(Airport destination) {
        this.destination = destination;
    }
}
