# Cordova Dual Sim SMS Plugin

Plugin for Cordova / PhoneGap to to easily send SMS. Available for **Android**.

## Installing the plugin


You can install it via repo url directly  :

```sh
cordova plugin add https://github.com/shahzadhamza/cordova-sms-plugin.git
```

## Using the plugin
HTML

```html
<input id="numberTxt" placeholder="Enter mobile number" value="" type="tel" />
<textarea id="messageTxt" placeholder="Enter message"></textarea>
<input type="button" onclick="app.sendSms()" value="Send SMS" />
```

Javascript

```js
var app = {
    sendSms: function() {
        var number = document.getElementById('numberTxt').value.toString(); /* iOS: ensure number is actually a string */
        var message = document.getElementById('messageTxt').value;
        console.log("number=" + number + ", message= " + message);

        //CONFIGURATION
        var options = {
            replaceLineBreaks: false, // true to replace \n by a new line, false by default
            android: {
                intent: 'INTENT'  // send SMS with the native android SMS messaging
                //intent: '' // send SMS without opening any other app
                slot: 0 // use 0 to send sms from sim1 and use 1 to send sms from sim2 
            }
        };

        var success = function () { alert('Message sent successfully'); };
        var error = function (e) { alert('Message Failed:' + e); };
        sms.send(number, message, options, success, error);
    }
};
```

On Android, two extra functions are exposed to know whether or not an app has permission and to request permission to send SMS (Android Marshmallow +).

```js
var app = {
    checkSMSPermission: function() {
        var success = function (hasPermission) { 
            if (hasPermission) {
                sms.send(...);
            }
            else {
                // show a helpful message to explain why you need to require the permission to send a SMS
                // read http://developer.android.com/training/permissions/requesting.html#explain for more best practices
            }
        };
        var error = function (e) { alert('Something went wrong:' + e); };
        sms.hasPermission(success, error);
    },
    requestSMSPermission: function() {
        var success = function (hasPermission) { 
            if (!hasPermission) {
                sms.requestPermission(function() {
                    console.log('[OK] Permission accepted')
                }, function(error) {
                    console.info('[WARN] Permission not accepted')
                    // Handle permission not accepted
                })
            }
        };
        var error = function (e) { alert('Something went wrong:' + e); };
        sms.hasPermission(success, error);
    }
};
```
