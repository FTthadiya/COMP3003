package edu.curtin.saed.assignment1;

// Defines the `FlightRequest` class, representing a flight request with destination and origin airport IDs.
public class FlightRequest {
    
    // Fields representing the destination and origin airport IDs for the flight request.
    private final int destinationAirportId;  
    private final int originAirportId;       

    // Constructor to initialize the destination and origin airport IDs.
    public FlightRequest(int destinationAirportId, int originAirportId) {
        this.destinationAirportId = destinationAirportId;  
        this.originAirportId = originAirportId;            
    }

    // Getter method to retrieve the destination airport ID.
    public int getDestinationAirportId() {
        return destinationAirportId;   
    }

    // Getter method to retrieve the origin airport ID.
    public int getOriginAirportId() {
        return originAirportId;        
    }
}
