module com.project.cmpt371 {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;


    opens com.project.cmpt371 to javafx.fxml;
    exports com.project.cmpt371;
}