metadata {
	definition (name: "Rooflight Controller", namespace: "CR76", author: "Cameron.Reid@Glazingvision.co.uk") {
        capability "Refresh"
        capability "Polling"
        capability "Switch"
        capability "Switch level"
        capability "Actuator"

        attribute "Rooflight", "string"
        attribute "Rain Sensor", "string"
        
        command "Stop"
        
    	fingerprint profileId: "0104", inClusters: "0000,0006,0008"    
	}

	// simulator metadata
	simulator {
    }

	// UI tile definitions
	tiles {
		multiAttributeTile(name:"sliderTile", type: "generic", width:6, height:4) {
    		tileAttribute("device.Actuator", key: "PRIMARY_CONTROL") {
        		attributeState "Open", label:'Open', action: "Switch.off", backgroundColor:"#79b821", nextState:"Closing", icon: "st.contact.contact.open"
        		attributeState "Closed", label:'Closed', action: "Switch.on", backgroundColor:"#ffffff", nextState:"Opening", icon: "st.contact.contact.closed"
        		attributeState "Opening", label:'Opening', backgroundColor:"#79b821", icon: "st.contact.contact.open"
        		attributeState "Closing", label:'Closing', backgroundColor:"#ffffff", icon: "st.contact.contact.open"
    		}
			tileAttribute ("device.level", key: "SECONDARY_CONTROL") {
				attributeState "default", label: 'Position ${currentValue}%'
            }
        }
        standardTile("Close", "device.Actuator", inactiveLabel: false, decoration: "flat", width:2, height:2 ) {
			state "default", label: 'Close', action:"Switch.off", icon:"st.thermostat.thermostat-left"
		}
        standardTile("Stop", "device.level", inactiveLabel: false, decoration: "flat", width:2, height:2 ) {
			state "default", action:"Stop", icon:"st.sonos.stop-btn"
		}
        standardTile("Open", "device.Actuator", inactiveLabel: false, decoration: "flat", width:2, height:2 ) {
			state "default", label: 'open', action:"Switch.on", icon:"st.thermostat.thermostat-right"
		}
        standardTile("Rain", "device.Rain Sensor", width: 2, height: 2) {
        	state "No", backgroundColor: "#ffffff"
    		state "Dry", label: 'Dry', icon:"st.Weather.weather14", backgroundColor: "#ffffff"
    		state "Wet", label: 'Raining', icon:"st.Weather.weather10", backgroundColor: "#153591"
		}
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:2, height:2 ) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
		main ("sliderTile")
		details (["sliderTile","Close","Stop","Open","Rain","refresh"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "Parse description $description"
    def name = null
    def value = null
 	
    if (description?.startsWith("catchall: 0104 0006 38")) {
        log.debug "On/Off command received from EP 38 (open/close command)"
        if (description?.endsWith("01 01 0000001000")) {
        	name = "Actuator"
        	value = "Closed"
        }
        else if (description?.endsWith("01 01 0000001001")) {
        	name = "Actuator"
            value = "Open"
        }
       else if (description?.endsWith("01 01 0000001003")) {
        	name = "Actuator"
            value = "Opening"
       } 
       else if (description?.endsWith("01 01 0000001004")) {
        	name = "Actuator"
            value = "Closing"
       } 
    }

    if (description?.startsWith("catchall: 0104 0006 39")) {
        log.debug "On/Off command received from EP 39 rain sensor"
        state.tile = 1															//Activate rain sensor tile and allow tile to be refreshed.
       	if (description?.endsWith("01 01 0000001000")) {
        	name = "Rain Sensor"
        	value = "Dry"
        }
        else if (description?.endsWith("01 01 0000001001")) {
        	name = "Rain Sensor"
        	value = "Wet"
        }
    }
    
    if (description?.startsWith("catchall: 0104 0006 40"))  {
        log.debug "On/Off command received from EP 40 Thermostat"
        if (description?.endsWith("01 01 0000001000")){
        	name = "Thermostat"
            value = "Cold"
        }
        else if (description?.endsWith("01 01 0000001001")) {
        	name = "Thermostat"
            value = "Hot"
        }
    }
    
    if(description?.startsWith('read attr -')) {
        def descMap = parseDescriptionAsMap(description) 						//Get level value.
        value = zigbee.convertHexToInt(descMap.value)
        log.debug " level = $value"
        name = "level"
    }
    def result = createEvent(name: name, value: value)
    log.debug "Parse returned ${result?.descriptionText}"
    return result 
}

def parseDescriptionAsMap(description) {
    (description - "read attr - ").split(",").inject([:]) {
    	map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}

// Commands to device
def on() {
	log.debug "Rooflight Opening"
    def cmd = []
	cmd <<"st cmd 0x${device.deviceNetworkId} 0x38 0x0006 0x1 {}"
}

def off() {
	log.debug "Rooflight Closing"
    def cmd = []
	cmd << "st cmd 0x${device.deviceNetworkId} 0x38 0x0006 0x0 {}"
}

def Stop() {																//Send stop command in the level control cluster.
	log.debug "send stop"
	"st cmd 0x${device.deviceNetworkId} 0x38 0x0008 0x07 {}"
}

def poll() {
	log.debug "Poll is calling refresh"
	refresh()
}

def refresh() {
    log.debug "sending refresh command"
    log.debug "Tile State: ${state.tile}"
    def cmd = []
    if("${state.tile}" == "0") {											//If rain sensor is connected refresh rain sensor.
    	cmd << "st rattr 0x${device.deviceNetworkId} 0x38 0x0006 0x0000"	// Read on / off attribute at End point 0x38 Rooflight open / closed.
    	cmd << "delay 250"
        cmd << "st rattr 0x${device.deviceNetworkId} 0x38 0x0008 0x0000"	// Read Level position at End point 0x38 Rooflight Position.
        cmd << "delay 250"
    	cmd << "st rattr 0x${device.deviceNetworkId} 0x39 0x0006 0x0000"	// Read on / off attribute at End point 0x39 Rain sensor.
    }
    else {
        cmd << "st rattr 0x${device.deviceNetworkId} 0x38 0x0006 0x0000"	// Read on / off attribute at End point 0x38 Rooflight open / closed.
        cmd << "delay 250"
        cmd << "st rattr 0x${device.deviceNetworkId} 0x38 0x0008 0x0000"	// Read Level position at End point 0x38 Rooflight Position.
    }
}