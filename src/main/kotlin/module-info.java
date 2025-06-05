module com.karpen.spotifyoverlay {
    requires javafx.controls;
    requires javafx.fxml;
    requires kotlin.stdlib;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;
    requires java.desktop;
    requires java.net.http;
    requires org.apache.httpcomponents.client5.httpclient5;
    requires org.apache.httpcomponents.core5.httpcore5;
    requires org.slf4j;
    requires org.json;
    requires jdk.httpserver;

    opens com.karpen.spotifyoverlay to javafx.fxml;
    exports com.karpen.spotifyoverlay;
}