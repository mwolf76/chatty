# Chatty - Instant Messaging for Work & Leisure

I started this project in 2017 to have something to experiment with
while learning to develop with the Vert.X toolkit
(http://vertx.io/). Chatty is a web application intended to act as an
online multi-room chat system.  Users can interact with each other in
realtime, create new rooms for distinct topics and download the whole
history of a room at any moment.

The application makes use of the following technologies:

* WebSockets (communication between the browser and the server)
* Kafka, for messaging persistence (history)
* Redis, for user presence detection
* MongoDB, for general data persistence

