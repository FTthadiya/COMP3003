package edu.curtin.saed.assignment1;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.animation.AnimationTimer;
import java.util.logging.Logger;
import java.util.function.Consumer;

// Defines the `AirTrafficSimulation` class, which handles the simulation air traffic, including plane movement, flight requests, and servicing.
public class AirTrafficSimulation {

    // Constants defining map dimensions, number of airports, planes per airport, and plane speed.
    private static final double MAP_WIDTH = 10.0;
    private static final double MAP_HEIGHT = 10.0;
    private static final int NUM_AIRPORTS = 10;
    private static final int PLANES_PER_AIRPORT = 10;
    private static final double PLANE_SPEED = 1;

    // Lists to hold airports and planes.
    private final List<Airport> airports;
    private final List<Plane> planes;

    // Grid area for visual representation and status text label for displaying Status information.
    private final GridArea gridArea;
    private final Label statusText;

    // Consumer for handling messages related to simulation events.
    private final Consumer<String> messageConsumer;

    // Executors and queues for handling flight requests, plane servicing, and plane movements.
    private static ExecutorService flightRequestExecutor;
    private static ExecutorService planeServicingExecutor;
    @SuppressWarnings("PMD.SingularField") // Suppressing PMD warning because 'planeMovementExecutor' is intentionally declared as a static field for reuse across multiple methods, ensuring thread pool management remains consistent and accessible throughout the application.
    private static ExecutorService planeMovementExecutor;
    private final static BlockingQueue<Runnable> PLANE_MOVEMENT_QUEUE  = new LinkedBlockingQueue<>();
    private final BlockingQueue<FlightRequest> flightRequestQueue = new LinkedBlockingQueue<>();
    private static AnimationTimer animationTimer;

    // Counters for tracking planes in flight, in service, and completed trips.
    private final AtomicInteger planesInFlight = new AtomicInteger(0);
    private final AtomicInteger planesInService = new AtomicInteger(0);
    private final AtomicInteger completedTrips = new AtomicInteger(0);

    private static final String PLANE_SERVICE_COMMAND = "saed_plane_service"; // Command for servicing planes.
    private static final String PLANE_REQUEST_COMMAND = "saed_flight_requests"; // Command for Flight Requests.
    private final static ExecutorService FLIGHT_REQUEST_PROCESSOR_EXECUTOR = Executors.newSingleThreadExecutor(); // Executor for processing flight requests.
    private static final Logger logger = Logger.getLogger(AirTrafficSimulation.class.getName()); // Logger for recording simulation events.
    private ScheduledExecutorService scheduledPlaneMovementExecutor; // Scheduled executor for managing timed plane movements.
    private final Map<Integer, List<Plane>> availablePlanesMap = new HashMap<>(); // Map for storing available planes at each airport by airport ID.
    private final List<Process> runningProcesses = new ArrayList<>(); // List to track running processes in the simulation.
    private final Map<Integer, Future<?>> airportFlightRequestFutures = new HashMap<>(); // Map to track future tasks associated with flight requests for each airport.
    private volatile boolean acceptingRequests = true; // Flag indicating whether the system is accepting flight requests.
    private boolean isSimulationRunning = false; // Flag indicating if the simulation is currently running.


    // Constructor for initializing the simulation with a grid area, status text label, and message consumer.
    public AirTrafficSimulation(GridArea gridArea, Label statusText, Consumer<String> messageConsumer) {
        this.gridArea = gridArea;
        this.airports = new ArrayList<>();
        this.planes = new ArrayList<>();
        this.statusText = statusText;
        initializeAirports();
        initializePlanes();
        this.messageConsumer = messageConsumer;

        // Initialize Executors
        flightRequestExecutor = Executors.newFixedThreadPool(NUM_AIRPORTS);
        planeServicingExecutor = Executors.newCachedThreadPool();
        planeMovementExecutor = Executors.newFixedThreadPool(PLANES_PER_AIRPORT * NUM_AIRPORTS);
        scheduledPlaneMovementExecutor = Executors.newScheduledThreadPool(1);

        // Start updating plane positions
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updatePlanePositions();
                updatePlaneDisplay();
            }
        };

        // Start processing queues for plane movement and flight requests.
        startPlaneMovementProcessor();
        startFlightRequestProcessor();
    }

    // Starts the simulation by launching the animation timer and flight request processes.
    public void startSimulation() {
        if (isSimulationRunning) {
            return; // Ignore if already running
        }
        
        isSimulationRunning = true;
        animationTimer.start();
        startFlightRequestProcesses();
        updateStatisticsDisplay();
    }

    // Initializes airports with random coordinates and adds them to the grid area.
    private void initializeAirports() {
        Random rand = new Random();
        for (int i = 1; i <= NUM_AIRPORTS; i++) {
            double x = rand.nextDouble() * (MAP_WIDTH - 1.0) + 0.5;
            double y = rand.nextDouble() * (MAP_HEIGHT - 1.0) + 0.5;
            Airport airport = new Airport(i, x, y);
            airports.add(airport);
            GridAreaIcon airportIcon = new GridAreaIcon(x, y, 0.0, 1.0,
                App.class.getClassLoader().getResourceAsStream("airport.png"), "Airport " + i);
            gridArea.getIcons().add(airportIcon);
        }
        Platform.runLater(gridArea::requestLayout);
    }

    // Initializes planes with random positions near their respective airports.
    private void initializePlanes() {
        Random rand = new Random();
        for (Airport airport : airports) {
            List<Plane> availablePlanes = new ArrayList<>();
            int startPlaneID = (airport.getId() - 1) * PLANES_PER_AIRPORT + 1;
            int endPlaneID = airport.getId() * PLANES_PER_AIRPORT;
            for (int planeID = startPlaneID; planeID <= endPlaneID; planeID++) {
                double x = airport.getX() + (rand.nextDouble() - 0.5);
                double y = airport.getY() + (rand.nextDouble() - 0.5);
                x = Math.max(0.5, Math.min(x, MAP_WIDTH - 0.5));
                y = Math.max(0.5, Math.min(y, MAP_HEIGHT - 0.5));
                Plane plane = new Plane(planeID, x, y, PLANE_SPEED, airport);
                plane.setCurrentAirport(airport);
                planes.add(plane);
                availablePlanes.add(plane); // Add plane to available planes list
            }
            availablePlanesMap.put(airport.getId(), availablePlanes); // Store available planes for each airport
        }
        Platform.runLater(gridArea::requestLayout);
    }
    
    // Updates the display of planes on the grid area. if in flight
    public void updatePlaneDisplay() {
        Platform.runLater(() -> {
            synchronized (planes) {
                for (Plane plane : planes) {
                    
                    if (plane.isInFlight()) {
                        GridAreaIcon icon = gridArea.getIcons().stream()
                            .filter(i -> i.getCaption().startsWith("Plane " + plane.getId()))
                            .findFirst()
                            .orElseGet(() -> new GridAreaIcon(plane.getX(), plane.getY(), 0.0, 1.0,
                                App.class.getClassLoader().getResourceAsStream("plane.png"), "Plane " + plane.getId()));
                        
                        icon.setPosition(plane.getX(), plane.getY());
                        if (!gridArea.getIcons().contains(icon)) {
                            gridArea.getIcons().add(icon);
                        }
                    }
                }
                gridArea.requestLayout();
            }
        });
    }
    
    // Updates the positions of planes based on their destination and speed.
    private void updatePlanePositions() {
        synchronized (planes) {
            Iterator<Plane> planeIterator = planes.iterator();
            while (planeIterator.hasNext()) {
                Plane plane = planeIterator.next();
                if (plane.isInFlight()) {
                    PLANE_MOVEMENT_QUEUE.offer(() -> {
                        synchronized (plane) {
                            Airport destination = plane.getDestination();
                            if (destination != null) {
                                double dx = destination.getX() - plane.getX();
                                double dy = destination.getY() - plane.getY();
                                double distance = Math.sqrt(dx * dx + dy * dy);
                                if (distance < 0.5) {
                                    plane.setInFlight(false);
                                    synchronized (this) {
                                        planesInFlight.getAndDecrement(); // Decrease the number of planes by one, currently in flight.
                                        updateStatisticsDisplay(); // Update the display showing the current statistics (planes in flight, in service, Complete trips).
                                    }
                                    plane.setCurrentAirport(destination);
                                    destination.addLandedPlane(plane);
                                    planeServicingExecutor.submit(() -> {
                                        servicePlane(plane, destination.getId());
                                    });
    
                                    Platform.runLater(() -> {
                                        gridArea.getIcons().removeIf(icon -> icon.getCaption().startsWith("Plane " + plane.getId()));
                                    });                                    
                                    return;
                                }

                                double moveX = (dx / distance) * plane.getSpeed();
                                double moveY = (dy / distance) * plane.getSpeed();
                                double newX = plane.getX() + moveX;
                                double newY = plane.getY() + moveY;
                                newX = Math.max(0.5, Math.min(newX, MAP_WIDTH - 0.5));
                                newY = Math.max(0.5, Math.min(newY, MAP_HEIGHT - 0.5));
                                plane.setX(newX);
                                plane.setY(newY);
                            }
                        }
                    });
                }
            }
        }
    }
    
    // Starts the flight request processes for each airport.
    public void startFlightRequestProcesses() {
    // Relative path to the executable
    String basePath = System.getProperty("user.dir") + "/comms/bin/";
    String relativeExecutablePath = basePath + PLANE_REQUEST_COMMAND + ".bat";
    for (Airport airport : airports) { // Iterate over each airport.
        int originAirportId = airport.getId();
        int zeroBasedIndex = originAirportId - 1; // Convert airport ID to zero-based index for command-line arguments.(0 to 9 only except by sead_flight_request.bat)
        Future<?> future = flightRequestExecutor.submit(() -> {
            ProcessBuilder processBuilder = new ProcessBuilder(relativeExecutablePath, String.valueOf(NUM_AIRPORTS), String.valueOf(zeroBasedIndex));
            processBuilder.directory(new File("comms/bin")); // Use relative directory
            Process process = null;
            try {
                logger.info(() -> "Starting saed_flight_requests process for Airport " + originAirportId + " with command: " + String.join(" ", processBuilder.command()));
                process = processBuilder.start();
                trackProcess(process); // Track the process
                handleFlightRequests(process, originAirportId);
            } catch (IOException e) {
                logger.severe(() -> "IOException occurred while starting process for Airport " + originAirportId + ": " + e.getMessage());
            } finally {
                if (process != null) { // Ensure the process is properly terminated.
                    process.destroy();
                    try {
                        process.waitFor();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        logger.warning(() -> "Interrupted while waiting for process termination for Airport " + originAirportId + ": " + e.getMessage());
                    }
                }
            }
        });
        airportFlightRequestFutures.put(originAirportId, future); // Store the Future associated with the process for later tracking.
    }
}

    // Handles flight requests from the command process for a given origin airport ID.
    private void handleFlightRequests(Process process, int originAirportId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) { // Read each line from the process output.
                // Check if the system is still accepting requests
                if (!acceptingRequests) {
                    logger.info(() -> "No longer accepting flight requests. Ignoring requests from Airport " + originAirportId);
                    break; // Exit the loop no longer accepting flight requests
                }

                try {
                    int destinationAirportId = Integer.parseInt(line) + 1; // Parse the destination airport ID from the line.
                    if (destinationAirportId >= 1 && destinationAirportId <= NUM_AIRPORTS) { // Check if the destination airport ID is valid.
                        if (!(destinationAirportId == originAirportId)) { // Ensure destination is different from the origin airport.
                            logger.info(() -> "Flight request from Airport " + originAirportId + " to Airport " + destinationAirportId);
                            flightRequestQueue.offer(new FlightRequest(destinationAirportId, originAirportId)); // Add the valid flight request to the queue.
                        } 
                    } else {
                        logger.warning(() -> "Invalid destination airport ID in flight request: " + destinationAirportId);
                    }
                } catch (NumberFormatException e) { // Handle cases where the line cannot be parsed as an integer.
                    logger.warning(() -> "Invalid number format in flight request from Airport " + originAirportId + ": " + e.getMessage());
                }
            }
        } catch (IOException e) { // Handle IOExceptions that may occur while reading from the process.
            logger.severe(() -> "IOException occurred while reading flight requests for Airport " + originAirportId + ": " + e.getMessage());
        } finally {
            // Ensure that resources related to the process are properly cleaned up if needed
            if (process.isAlive()) {
                process.destroy(); // Destroy the process if it's still running.
            }
        }
    }

    // Starts the processing of flight requests.
    private void startFlightRequestProcessor() {
        // Submit a task to the flight request processor executor.
        FLIGHT_REQUEST_PROCESSOR_EXECUTOR.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!acceptingRequests && flightRequestQueue.isEmpty()) {
                        break; // Exit if not accepting requests and queue is empty
                    }
                    FlightRequest flightRequest = flightRequestQueue.take();
                    logger.info(() -> "Processing flight request to Airport " + flightRequest.getDestinationAirportId() + " from Airport " + flightRequest.getOriginAirportId());
                    processFlightRequest(flightRequest.getDestinationAirportId(), flightRequest.getOriginAirportId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }
    

    // Processes a flight request by finding available planes and assigning them to the request.
    private void processFlightRequest(int destinationAirportId, int originAirportId) {
        synchronized (planes) {
            if (destinationAirportId < 1 || destinationAirportId > NUM_AIRPORTS) {
                logger.warning(() -> "Received invalid destination airport ID: " + destinationAirportId);
                return;
            }
    
            List<Plane> availablePlanes = availablePlanesMap.get(originAirportId);
            if (availablePlanes != null && !availablePlanes.isEmpty()) {
                Plane availablePlane = availablePlanes.remove(0); // Get and remove the first available plane
    
                synchronized (this) {
                    planesInFlight.getAndIncrement();
                    updateStatisticsDisplay();
                }
    
                availablePlane.setInFlight(true);
                availablePlane.setDestination(airports.get(destinationAirportId - 1));
    
                logger.info(() -> "Assigned Plane " + availablePlane.getId() + " to flight from Airport " + originAirportId + " to Airport " + destinationAirportId);
                messageConsumer.accept("Departure: Plane No "+ availablePlane.getId()+" From Airport " + originAirportId + " --> Airport " + destinationAirportId);
    
            } else {
                logger.warning(() -> "No available plane at Airport " + originAirportId + " for request to Airport " + destinationAirportId);
            }
        }
    }
    
   // Start processing plane movements using ScheduledExecutorService
    private void startPlaneMovementProcessor() {
        scheduledPlaneMovementExecutor.scheduleAtFixedRate(() -> {
            synchronized (planes) {
                Iterator<Plane> planeIterator = planes.iterator();
                while (planeIterator.hasNext()) {
                    Plane plane = planeIterator.next();
                    if (plane.isInFlight()) {
                        Airport destination = plane.getDestination();
                        if (destination != null) {
                            double dx = destination.getX() - plane.getX();
                            double dy = destination.getY() - plane.getY();
                            double distance = Math.sqrt(dx * dx + dy * dy);
                            if (distance < 0.5) {
                                plane.setInFlight(false);
                                planesInFlight.getAndDecrement();
                                updateStatisticsDisplay();
                                planeServicingExecutor.submit(() -> servicePlane(plane, destination.getId()));
                                Platform.runLater(() -> gridArea.getIcons().removeIf(icon -> icon.getCaption().startsWith("Plane " + plane.getId())));
                                messageConsumer.accept("Landing: Plane No " + plane.getId() + " at Airport " + destination.getId());
                                logger.info(() -> "Landing: Plane " + plane.getId() + " has landed at Airport " + destination.getId());

                            } else {
                                double moveX = (dx / distance) * plane.getSpeed();
                                double moveY = (dy / distance) * plane.getSpeed();
                                double newX = plane.getX() + moveX;
                                double newY = plane.getY() + moveY;
                                newX = Math.max(0.5, Math.min(newX, MAP_WIDTH - 0.5));
                                newY = Math.max(0.5, Math.min(newY, MAP_HEIGHT - 0.5));
                                plane.setX(newX);
                                plane.setY(newY);
                            }
                        }
                    }
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    // Services a plane when it lands.
    private void servicePlane(Plane plane, int airportId) {
        // Relative path to the executable
        String basePath = System.getProperty("user.dir") + "/comms/bin/";
        String relativeExecutablePath = basePath + PLANE_SERVICE_COMMAND + ".bat";
        ProcessBuilder processBuilder = new ProcessBuilder(relativeExecutablePath, String.valueOf(airportId), String.valueOf(plane.getId()));
        processBuilder.directory(new File("comms/bin")); // Use relative directory
        Process process = null;
        try {
            planesInService.getAndIncrement();
            logger.info(() -> "Starting plane servicing process for Plane " + plane.getId() + " at Airport " + airportId);
            // Notify that servicing for the plane has started
            Platform.runLater(() -> messageConsumer.accept("Service Start : Starting servicing for Plane " + plane.getId() + " at Airport " + airportId));
            process = processBuilder.start();
            captureServiceOutput(process, plane.getId());
            process.waitFor();
            synchronized (planes) {
                plane.setInFlight(false);
                // Add the plane to the available planes map
                Airport airport = airports.get(airportId - 1);
                airport.addAvailablePlane(plane);
                availablePlanesMap.get(airportId).add(plane); // Update available planes map 
            }
        } catch (IOException e) {
            logger.severe(() -> "IOException occurred while starting plane servicing process for Plane " + plane.getId() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warning(() -> "Interrupted while waiting for plane servicing process termination for Plane " + plane.getId() + ": " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }


    // Capturing output from the service process for a specific plane
    private void captureServiceOutput(Process process, int planeId) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                handleServiceOutput(line, planeId);
            }
        } catch (IOException e) {
            logger.severe(() -> "IOException occurred while capturing service output for Plane " + planeId + ": " + e.getMessage());
        }
    }

   // Handle a single line of service output for a specific plane.
    private void handleServiceOutput(String line, int planeId) {
        logger.info(() -> "Service message for Plane " + planeId + ": " + line);

        // Synchronize updates to shared resources
        synchronized (this) {
            messageConsumer.accept("Service End : " + line);
            planesInService.getAndDecrement();
            completedTrips.getAndIncrement();
        }
    }


    // Updates the UI with current simulation Status.
    private void updateStatisticsDisplay() {
        synchronized (this) {
            // Updates the statusText label with the current counts of planes in flight, in service, and completed trips.
            Platform.runLater(() -> statusText.setText(
                "Planes In Flight: " + planesInFlight + ", Planes In Service: " + planesInService + ", Completed Trips: " + completedTrips));
        }
    }

    // Method to stop the simulation when the 'End' button is pressed
    public void stopSimulation() {
        if (!isSimulationRunning) {
            return; // Ignore if the simulation is not running
        }

        acceptingRequests = false; // Stop accepting new requests
        isSimulationRunning = false;

        // Clear the queues for flight requests and plane movements
        flightRequestQueue.clear();
        PLANE_MOVEMENT_QUEUE.clear();

        // Runnig shutdown process on a separate thread to avoid blocking the JavaFX thread
        new Thread(() -> {
            try {
                // Wait for all planes to land before proceeding
                while (planesInFlight.get() > 0) {
                    Thread.sleep(100);
                }

                // Wait for all planes in service to complete before proceeding
                while (planesInService.get() > 0) {
                    Thread.sleep(100); 
                }

                Platform.runLater(() -> {
                    // update completed trips 
                    updateStatisticsDisplay();
                });

                // Gracefully shutdown the plane movement executor
                planeMovementExecutor.shutdown();
                if (!planeMovementExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                    planeMovementExecutor.shutdownNow(); // Force shutdown if tasks take too long
                }

                // Shutdown other executors for flight requests and servicing
                flightRequestExecutor.shutdown();
                planeServicingExecutor.shutdown();

                // Stop remaining external processes gracefully
                stopExternalProcesses();

                // Signal that shutdown is complete
                shutdownLatch.countDown();

                // Update the UI from the JavaFX thread after the shutdown is complete
                Platform.runLater(this::updateStatisticsDisplay);
                
            } catch (InterruptedException e) {
                planeMovementExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }).start(); // Start the shutdown process on a new thread
    }


    // method to track the process when it starts
    private void trackProcess(Process process) {
        synchronized (runningProcesses) {
            runningProcesses.add(process);
        }
    }

   // Method to stop all external processes
    public void stopExternalProcesses() {
    // Stop flight request processes
    synchronized (runningProcesses) {
        for (Process process : runningProcesses) {
            if (process.isAlive()) {
                process.destroy(); // Try to gracefully terminate the process
                try {
                    process.waitFor(5, TimeUnit.SECONDS); // Wait for the process to terminate
                    if (process.isAlive()) {
                        process.destroyForcibly(); // Forcefully terminate if it doesn't stop
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warning(() -> "Interrupted while waiting for external process to terminate: " + e.getMessage());
                }
            }
        }
        runningProcesses.clear();
    }

    // Shutdown all executors to stop task execution
    try {
        if (!flightRequestExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            flightRequestExecutor.shutdownNow();
        }
        if (!planeServicingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            planeServicingExecutor.shutdownNow();
        }
        if (!planeMovementExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            planeMovementExecutor.shutdownNow();
        }
        if (!scheduledPlaneMovementExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
            scheduledPlaneMovementExecutor.shutdownNow();
        }
        if (!FLIGHT_REQUEST_PROCESSOR_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
            FLIGHT_REQUEST_PROCESSOR_EXECUTOR.shutdownNow();
        }
    } catch (InterruptedException e) {
        flightRequestExecutor.shutdownNow();
        planeServicingExecutor.shutdownNow();
        planeMovementExecutor.shutdownNow();
        scheduledPlaneMovementExecutor.shutdownNow();
        FLIGHT_REQUEST_PROCESSOR_EXECUTOR.shutdownNow();
        Thread.currentThread().interrupt();
     }
    }  


    // Retrieves the list of planes.
    public List<Plane> getPlanes() {
        return planes;
    }
    
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    public void closeSimulation() {
        if (!isSimulationRunning) {
            Platform.runLater(Platform::exit);        
        }
        new Thread(() -> {
            try {
                // Stop the simulation and wait for it to complete
                stopSimulation();
                
                // Wait for the shutdown process to complete
                shutdownLatch.await(5, TimeUnit.MINUTES); // Adjust timeout as needed
                
                // Now exit the application
                Platform.runLater(Platform::exit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    
    } 
}

