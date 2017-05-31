'use strict';
window.WebChat = window.WebChat || {};

window.WebChat.Channel = (function() {
    var eventBus;
    var userID;
    var roomID;

    var usersMap;

    /**
     * Channel initialization
     *
     * @param {Object} params
     */
    var init = function(params) {

        eventBus = new EventBus("/eventbus/");
        eventBus.onopen = function () {
            eventBus.registerHandler("webchat.client", function (err, msg) {

                var messageRoomID = msg.body.roomID;
                if (messageRoomID != roomID)
                    return; /* discard */

                var $textarea = $('#room');
                $textarea.append(msg.body.displayText);
                $textarea.scrollTop($textarea[0].scrollHeight);
            });

            eventBus.registerHandler("webchat.partakers." + roomID, function (err, msg) {
                $('#partakers').html('');
                _.each(msg.body.users, function(userID) {
                    if (userID in usersMap) {
                        appendUser(usersMap[userID]);
                    } else {
                        eventBus.send("webchat.data-store", {
                            type: "find-user-by-uuid",
                            params: {
                                uuid: userID
                            }
                        }, function(err, msg) {
                            var email = msg.body.result.email;
                            usersMap[userID] = email;
                            appendUser(usersMap[userID]);
                        });
                    }
                });
            });

            eventBus.registerHandler("webchat.rooms", function (err, msg) {
                $('#rooms').html('');
                _.each(msg.body.rooms, function(room) {
                    appendRoom(room);
                });
            });

            /* presence heartbeat (500 ms) */
            setInterval(function() {
                eventBus.publish("webchat.presence", {
                    type: 'update-presence',
                    params: {
                        userID: userID,
                        roomID: roomID
                    }
                });
            }, 500);
        };

        userID = params.userID;
        roomID = params.roomID;

        usersMap = {};

        $('#user').keyup(function (event) {
            if (event.keyCode == 13 || event.which == 13) {
                var txt = $('#user').val();
                if (txt.length > 0) {

                    eventBus.publish("webchat.server", JSON.stringify({
                        userID: userID,
                        roomID: roomID,
                        text: txt
                    }));

                    /* clear input */
                    $('#user').val("");
                }
            }
        });

        $.ajax({
            type: 'GET',
            url: '/protected/history/' + roomID
        }).then(function (body) {
            var $textarea = $('#room');
            body.data.history.forEach(function(msg, index, array) {
                $textarea.append(msg);
            });
            $textarea.scrollTop($textarea[0].scrollHeight);
            console.log( '' + body.data.history.length + " history messages loaded");

            document.getElementById("user").focus();
        })

        /* add collections */
        $('#commit-create-room')
            .on('click', function (event) {
                var roomName = $('#additional-room-input').val();
                putRoom(roomName, function() {
                    $('#add-room-modal').modal('hide');
                });
            });
    };

    function appendUser(email) {
        var list = document.getElementById('partakers');

        var entry = document.createElement('li');
        entry.className += ' list-group-item';
        entry.appendChild(document.createTextNode(email));

        list.appendChild(entry);
    }

    function appendRoom(room) {
        var list = document.getElementById('rooms');

        var entry = document.createElement('li');
        entry.className += ' list-group-item';

        var anchor = document.createElement('a');
        anchor.href = '/protected/rooms/' + room.uuid;

        var text = document.createTextNode(room.name);
        anchor.appendChild(text);
        entry.appendChild(anchor);
        list.appendChild(entry);
    }


    /*** ajax helpers ***/
    function defaultErrorCallback(jqXHR, textStatus, errorThrown) {
        console.warn(jqXHR.responseText);
    }

    function putRoom(roomName, successCallback, errorCallback) {
        $.ajax({
            type: "PUT",
            url: "/protected/new-room",
            data: {
                roomName: roomName
            },
            success: successCallback,
            error: errorCallback || defaultErrorCallback
        });
    }

    return {
        'init': init
    }
})();
