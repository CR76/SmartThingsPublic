metadata {
	definition (name: "Mbed Rooflight Simulator 3", namespace: "CR76", author: "Cameron Reid") {
        capability "Refresh"
        capability "Polling"
        capability "Sensor"
        capability "Switch"
        capability "Temperature Measurement"
        capability "Switch level"
        
        attribute "status", "string"
        attribute "sensor", "string"
        
    	fingerprint endpointId: "77 , 88", profileId: "0104",inClusters: "0000,0006,0008,0402"
	}

	// simulator metadata
	simulator {
    }

	// UI tile definitions
	tiles {
		multiAttributeTile(name:"sliderTile", type:"generic", width:6, height:4) {
    		tileAttribute("device.switch", key: "PRIMARY_CONTROL") {
        	attributeState "on", label:'${name}', action: "Switch.off", backgroundColor:"#79b821", nextState:"turningOff"
        	attributeState "off", label:'${name}', action: "Switch.on", backgroundColor:"#ffffff", nextState:"turningOn"
        	attributeState "turningOn", label:'${name}', backgroundColor:"#79b821", nextState:"turningOff"
        	attributeState "turningOff", label:'${name}', backgroundColor:"#ffffff", nextState:"turningOn"
    	}
    	tileAttribute("device.level", key: "SECONDARY_CONTROL") {
        	attributeState "default", icon: 'st.Weather.weather1', label:'${currentValue}%', unit: "%" //,action:'${currentValue}'
    	}
    	tileAttribute("device.level", key: "SLIDER_CONTROL", range:"(20..80)") {
        	attributeState "level", action:"switch level.setLevel"
    	}
        }
        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width:2, height:2 ) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        standardTile("Rain", "device.status", width: 2, height: 2) {
    		state "Dry", label: 'Dry', icon:"st.Weather.weather14", backgroundColor: "#ffffff"
    		state "Wet", label: 'Raining', icon:"st.Weather.weather10", backgroundColor: "#153591"
		}
        
        valueTile("temperature", "device.temperature", width:2, height:2) {
			state("temperature", label:'${currentValue}°',
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
        }
        valueTile("Potential", "device.sensor", width:2, height:2) {
			state "value", label:'${currentValue}'
            }
		main (["sliderTile"])
		details (["sliderTile", "refresh", "temperature", "Rain", "Potential"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug "Parse description $description"
    def name = null
    def value = null
    Map map = [:]
 
    if (description?.startsWith("catchall: 0104 0006 77")) {
        log.debug "On/Off command received from EP 1"
        if (description?.endsWith("01 01 0000001000")){
        	name = "switch"
            value = "off"}
        else if (description?.endsWith("01 01 0000001001")){
        	name = "switch"
            value = "on"}
    def result = createEvent(name: name, value: value)
    log.debug "Parse returned ${result?.descriptionText}"
    return result
    }
    if (description?.startsWith("catchall: 0104 0006 88")) {
        log.debug "On/Off command received from EP 1"
        if (description?.endsWith("01 01 0000001000")){
        	name = "status"
            value = "Dry"}
        else if (description?.endsWith("01 01 0000001001")){
        	name = "status"
            value = "Wet"}
    def result = createEvent(name: name, value: value)
    log.debug "Parse returned ${result?.descriptionText}"
    return result
    }

    if (description?.startsWith("catchall: 0104 0402 77")) {
    		log.debug "get temperature"
			map = parseCatchAllMessage(description)
            log.debug "Parse returned $map"
            map ? createEvent(map) : null
            }
    else if(description?.startsWith('read attr -'))	{
    		log.debug "get Level"
            def descMap = parseDescriptionAsMap(description)
            value = zigbee.convertHexToInt(descMap.value)
            log.debug(" cluster = $descMap.cluster")
            if( "0x${descMap.cluster}" == "0x0008"){
            	log.debug " level = $value"
            	name = "level"
            }
            if( "0x${descMap.cluster}" == "0x000C"){
            	log.debug " Sensor = $value"
            	name = "sensor"
            }
            createEvent(name: name, value: value)    
    }
}

private Map parseCatchAllMessage(String description) {
    Map resultMap = [:]
    def cluster = zigbee.parse(description)

    switch(cluster.clusterId) {
        case 0x0402:
        // temp is last 2 data values. reverse to swap endian
        String temp = cluster.data[-2..-1].reverse().collect { cluster.hex1(it) }.join()
        log.debug "temp = $temp"
        def value = Integer.parseInt(temp, 16)
        resultMap = getTemperatureResult(value)
        break
    }

    return resultMap
}

def parseDescriptionAsMap(description) {
    (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
    }
}

private Map getTemperatureResult(value) {
	def linkText = getLinkText(device)
	if (tempOffset) {
		def offset = tempOffset as int
		def v = value as int
		value = v + offset
	}
	def descriptionText = "${linkText} was ${value}°${temperatureScale}"
    log.debug " temp $value"
	return [
		name: 'temperature',
		value: value,
		descriptionText: descriptionText
	]
}

// Commands to device

def on() {
	log.debug "Relay 1 on()"
	sendEvent(name: "switch", value: "on")
	"st cmd 0x${device.deviceNetworkId} 0x77 0x0006 0x1 {}"
}

def off() {
	log.debug "Relay 1 off()"
	sendEvent(name: "switch", value: "off")
	"st cmd 0x${device.deviceNetworkId} 0x77 0x0006 0x0 {}"
    //zigbee.command(0x0008, 0x04, "00")
}

def setLevel(value) {
	log.debug "Set Level $value"
	//zigbee.setLevel(value)
    "st cmd 0x${device.deviceNetworkId} 0x77 0x0008 $value {}"
    //zigbee.command(0x0008, 0x04, "$value")
}

def poll(){
	log.debug "Poll is calling refresh"
	refresh()
}

def refresh() {
	log.debug "sending refresh command"
    def cmd = []	
    cmd << "st rattr 0x${device.deviceNetworkId} 0x77 0x0006 0x0000"	// Read on / off attribute at End point 0x38
    cmd << "delay 250"
	cmd << "st rattr 0x${device.deviceNetworkId} 0x77 0x0402 0x0000"    // Read probe 1 Temperature 
 	cmd << "delay 250"
	cmd << "st rattr 0x${device.deviceNetworkId} 0x77 0x0008 0x0000"    // Read slider
    cmd << "delay 250"
	cmd << "st rattr 0x${device.deviceNetworkId} 0x77 0x000C 0x0055"    // Read slider 
    //cmd << "delay 250"
    //cmd << zigbee.levelRefresh()
    //cmd << zigbee.levelConfig()
}

