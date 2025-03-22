module com.project.cmpt371 {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.project.cmpt371 to javafx.fxml;
    exports com.project.cmpt371;
}