package edu.curtin.saed.assignment1;

import java.util.ArrayList;
import java.util.List;

// Defines the `Airport` class, representing an airport with a unique ID and coordinates (x, y).
public class Airport {
    // Fields representing the unique ID and coordinates of the airport.
    private final int id;        
    private final double x;        
    private final double y;        
    
    // Lists to store available planes and planes that have landed at the airport.
    private final List<Plane> availablePlanes = new ArrayList<>(); 
    private final List<Plane> landedPlanes = new ArrayList<>();   

    // Constructor to initialize the airport's ID and coordinates.
    public Airport(int id, double x, double y) {
        this.id = id;
        this.x = x;
        this.y = y;
    }

    // Adds a plane to the list of landed planes in a thread-safe manner.
    public void addLandedPlane(Plane plane) {
        synchronized (landedPlanes) {    
            landedPlanes.add(plane);     
        }
    }

    // Adds a plane to the list of available planes in a thread-safe manner.
    public void addAvailablePlane(Plane plane) {
        synchronized (availablePlanes) { 
            availablePlanes.add(plane); 
        }
    }

    // Returns a copy of the list of available planes in a thread-safe manner.
    public List<Plane> getAvailablePlanes() {
        synchronized (availablePlanes) {   
            return new ArrayList<>(availablePlanes);  
        }
    }

    // Getter methods to access the ID and coordinates of the airport.
    public int getId() { return id; }      
    public double getX() { return x; }     
    public double getY() { return y; }      
}
