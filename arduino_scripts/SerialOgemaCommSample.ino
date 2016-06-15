int arduinoId = 12345;
String address = "ogm::1 ";
int blub = 5;
String inputString = "";         // a string to hold incoming data
unsigned long previousMillis = 0; 
const long interval = 5000;


// the setup routine runs once when you press reset:
void setup() {
  // initialize serial communication at 9600 bits per second:
  Serial.begin(9600);
  pinMode(13, OUTPUT);
  // reserve 100 bytes for the inputString:
  inputString.reserve(100);
}

// the loop routine runs over and over again forever:
void loop() {
   unsigned long currentMillis = millis();

  if (currentMillis - previousMillis >= interval) {
    // save the last time you blinked the LED
    previousMillis = currentMillis;
  
    // read the input on analog pin 0:
    if (blub == 0) {
      blub = 5;
      digitalWrite(13, HIGH);
    }
    else {
      blub = 0;
      digitalWrite(13, LOW);
    }
    // print out the value you read:
    Serial.println(address + blub);
  }
}

void serialEvent() {
  while (Serial.available()) {
    // get the new byte:
    char inChar = (char)Serial.read();
    // add it to the inputString:
    inputString += inChar;
    // if the incoming character is a newline, set a flag
    // so the main loop can do something about it:
    if (inChar == '\n') {
//     Serial.println("Arduino received " + inputString); // must not spoil expected String
       if (inputString ==  "sendId\n") {
         Serial.println("id " + String(arduinoId));
       }   
       else if (inputString.startsWith("ogm::") && inputString.length() > 7) {
         String address = "";
         String sub = inputString.substring(5);
         short idx = 0;
         char idChar = sub.charAt(idx);
         while (idChar != ' ') {
            idx++;
            address += idChar;
            idChar = sub.charAt(idx);
         }
         String value = sub.substring(idx+1);
         Serial.println("New value for address " + address + ": " + value);     
         if(address == "13") {
            if (value == "0\n" || value == "false\n") 
                digitalWrite(13, LOW);
            else
                digitalWrite(13, HIGH);
         }       
       }
       inputString = "";
    }
  }
}
