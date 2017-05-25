'use strict';
window.WebChat = window.WebChat || {};

window.WebChat.Channel = (function() {
    var eventBus;
    var userID;
    var roomID;
    var historyURL;

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
                    return;

                var $textarea = $('#room');
                $textarea.append(msg.body.displayText);
                $textarea.scrollTop($textarea[0].scrollHeight);
            });
        };

        userID = params.userID;
        roomID = params.roomID;
        historyURL = params.historyURL;

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
            url: historyURL
        }).then(function (body) {
            var $textarea = $('#room');
            body.data.history.forEach(function(msg, index, array) {
                $textarea.append(msg);
            });
            $textarea.scrollTop($textarea[0].scrollHeight);
            console.log( '' + body.data.history.length + " messages loaded");

            document.getElementById("user").focus();
        })
    };

    return {
        'init': init
    }
})();
