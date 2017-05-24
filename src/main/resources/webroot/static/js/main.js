'use strict';
window.WebChat = window.WebChat || {};

window.WebChat.Channel = (function() {
    var eventBus;
    var userID;

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

        $('#user').keyup(function (event) {
            if (event.keyCode == 13 || event.which == 13) {
                var txt = $('#user').val();
                if (txt.length > 0) {

                    eventBus.publish("webchat.server", JSON.stringify({
                        userID: userID,
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

