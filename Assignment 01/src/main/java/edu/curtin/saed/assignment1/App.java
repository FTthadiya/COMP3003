package edu.curtin.saed.assignment1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * This is demonstration code intended for you to modify. Currently, it sets up a rudimentary
 * JavaFX GUI with the basic elements required for the assignment.
 *
 * (There is an equivalent Swing version of this, which you can use if you have trouble getting
 * JavaFX as a whole to work.)
 *
 * You will need to use the GridArea object, and create various GridAreaIcon objects, to represent
 * the on-screen map.
 *
 * Use the startBtn, endBtn, statusText and textArea objects for the other input/output required by
 * the assignment specification.
 *
 * Break this up into multiple methods and/or classes if it seems appropriate. Promote some of the
 * local variables to fields if needed.
 */

    public class App extends Application {
        private boolean isSimulationRunning = false;
        private TextArea textArea; // Add a field for the TextArea

        public static void main(String[] args) {
            launch();
        }

        @Override
        public void start(Stage stage) {
            GridArea area = new GridArea(10, 10);
            area.setStyle("-fx-background-color: #006000;");
            var statusText = new Label("Status : ");
            textArea = new TextArea(); // Initialize the TextArea
            //passing the area, statusText, and method reference for appending messages.
            AirTrafficSimulation simulation = new AirTrafficSimulation(area, statusText, this::appendToTextArea);

            var startBtn = new Button("Start");
            var endBtn = new Button("End");

            // Start Button Action
            startBtn.setOnAction((event) -> {
                // Check if the simulation is not already running
                if (!isSimulationRunning) {
                    System.out.println("Start button pressed");  
                    // Start the simulation  
                    simulation.startSimulation(); 
                    // Update the flag to indicate the simulation is running                 
                    isSimulationRunning = true;  
                    // Disable the Start button                  
                    startBtn.setDisable(true); 
                    // Enable the End button             
                    endBtn.setDisable(false);            
                }
            });

            // End Button Action
            endBtn.setOnAction((event) -> {
                // Check if the simulation is currently running
                if (isSimulationRunning) {
                    System.out.println("End button pressed");  
                    // Stop the simulation   
                    simulation.stopSimulation(); 
                    // Update the flag to indicate the simulation has stopped                 
                    isSimulationRunning = false; 
                    // Enable the Start button                 
                    startBtn.setDisable(false); 
                    // Disable the End button           
                    endBtn.setDisable(true);           
                }
            });

            // Initial button states
            startBtn.setDisable(false);   
            endBtn.setDisable(true);     

            // Handle Close Button Action
            stage.setOnCloseRequest((event) -> {
                System.out.println("Close button pressed"); 
                endBtn.setDisable(true);           
                // Ensure the simulation is stopped before closing 
                event.consume();   
                simulation.closeSimulation();

            });

            var toolbar = new ToolBar();
            toolbar.getItems().addAll(startBtn, endBtn, new Separator(), statusText);

            var splitPane = new SplitPane();
            splitPane.getItems().addAll(area, textArea);
            splitPane.setDividerPositions(0.75);

            stage.setTitle("Air Traffic Simulator");
            var contentPane = new BorderPane();
            contentPane.setTop(toolbar);
            contentPane.setCenter(splitPane);

            var scene = new Scene(contentPane, 1200, 1000);
            stage.setScene(scene);
            stage.show();
        }

        // Append text to the TextArea (Departures,Landings,Service Messages)
        private void appendToTextArea(String message) {
            Platform.runLater(() -> textArea.appendText(message + "\n"));
        }
    }

