'use strict';
window.WebChat = window.WebChat || {};

window.WebChat.Channel = (function() {
    var eventBus;
    var userID;
    var roomID;

    /**
     * Main entry point
     *
     */
    var init = function(params) {

        eventBus = new EventBus("/eventbus/");
        eventBus.onopen = function () {
            eventBus.registerHandler("webchat.client", function (err, msg) {
                $('#room').append(msg.body);
            });
        };

        userID = params.userID;
        roomID = 'bfae8b5c-d4cb-4c4f-b71f-a165c60bd684'; /* TODO: develop the rooms concept, const magic will do for now */
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
    };

    return {
        'init': init
    }
})();

