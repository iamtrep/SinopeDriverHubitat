/**
 *  Sinope TH1123ZB, TH1124ZB Device Driver for Hubitat
 *  Source: https://raw.githubusercontent.com/sacua/SinopeDriverHubitat/main/drivers/TH112xZB_Sinope_Hubitat.groovy
 *
 *  Code derived from Sinope's SmartThing thermostat for their Zigbee protocol requirements and from the driver of scoulombe79 and kris2k2
 *  Source: https://support.sinopetech.com/en/wp-content/uploads/sites/4/2019/08/Sinope-Technologies-Zigbee-Thermostat-V.1.0.0-SVN-547-1.txt
 *  Source: https://github.com/scoulombe79/Hubitat/blob/master/Drivers/Thermostat-Sinope-TH1123ZB.groovy
 *  Source: https://github.com/kris2k2/hubitat/blob/master/drivers/kris2k2-Sinope-TH112XZB.groovy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * v1.0.0 Initial commit
 * v1.1.0 Dont remember
 * v1.2.0 Correction for attribute reading for heat and off command
 * v1.3.0 Correction for the offset calculation very rarely, the reading is a super large negative value, when that happen, the offset does not change
 * v1.4.0 Enable debug parse, send event at configure for the supported mode and possible to reset the offset value
 * v1.5.0 Enable custom time for reset and manual reset
 * v1.5.1 Correction of bug for the reset of energy meter
 * v1.6.0 fix backlight control for G2 thermostat
 */

metadata {
	definition(name: "Thermostat TH112xZB with energy meter", namespace: "sacua", author: "Samuel Cuerrier Auclair") {
		capability "Thermostat"
		capability "Configuration"
		capability "TemperatureMeasurement"
		capability "Refresh"
		capability "Lock"
		capability "PowerMeter"
		capability "EnergyMeter"
		capability "Notification" // Receiving temperature notifications via RuleEngine

		attribute "outdoorTemp", "number"
		attribute "heatingDemand", "number"
		attribute "cost", "number"
		attribute "dailyCost", "number"
		attribute "weeklyCost", "number"
		attribute "monthlyCost", "number"
		attribute "yearlyCost", "number"
		attribute "dailyEnergy", "number"
		attribute "weeklyEnergy", "number"
		attribute "monthlyEnergy", "number"
		attribute "yearlyEnergy", "number"
		attribute "maxPower", "number"

		command "refreshTime" //Refresh the clock on the thermostat
        command "setClockTime" //Same as above, for compatibility with built-in driver (e.g. for Rule Machine)
		command "displayOn"
		command "displayOff"
        command "displayAdaptive"
		command "refreshTemp" //To refresh only the temperature reading
		command "resetEnergyOffset", ["number"]
		command "resetDailyEnergy"
		command "resetWeeklyEnergy"
		command "resetMonthlyEnergy"
		command "resetYearlyEnergy"

		preferences {
            input name: "prefBacklightMode", type: "enum", title: "Display backlight", options: ["off": "On Demand", "adaptive": "Adaptive (default)", "on": "Always On"], defaultValue: "adaptive", required: true, submitOnChange: true
            input name: "prefSecondTempDisplay", type: "enum", title: "Secondary Temp. Display", options:["auto": "Auto", "setpoint": "Setpoint (default)", "outdoor": "Outdoor"], defaultValue: "setpoint", required: true, submitOnChange: true
			input name: "prefTimeFormatParam", type: "enum", title: "Time Format", options:["24h", "12h AM/PM"], defaultValue: "24h", multiple: false, required: true
			input name: "tempChange", type: "number", title: "Temperature change", description: "Minumum change of temperature reading to trigger report in Celsius/100, 5..50", range: "5..50", defaultValue: 50
			input name: "HeatingChange", type: "number", title: "Heating change", description: "Minimum change in the PI heating in % to trigger power and PI heating reporting, 1..25", range: "1..25", defaultValue: 5
			input name: "energyChange", type: "number", title: "Energy increment", description: "Minimum increment of the energy meter in Wh to trigger energy reporting, 10..*", range: "10..*", defaultValue: 10
			input name: "energyPrice", type: "float", title: "c/kWh Cost:", description: "Electric Cost per Kwh in cent", range: "0..*", defaultValue: 9.38
			input name: "weeklyReset", type: "enum", title: "Weekly reset day", description: "Day on which the weekly energy meter return to 0", options:["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"], defaultValue: "Sunday", multiple: false, required: true
            input name: "yearlyReset", type: "enum", title: "Yearly reset month", description: "Month on which the yearly energy meter return to 0", options:["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"], defaultValue: "January", multiple: false, required: true
            input name: "txtEnable", type: "bool", title: "Enable logging info", defaultValue: true
		}

        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0003,0004,0005,0201,0204,0402,0702,0B04,0B05,FF01", outClusters: "000A,FF01,0019", model: "TH1123ZB", manufacturer: "Sinope Technologies", deviceJoinName: "Sinope Thermostat TH1123ZB"
        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0003,0004,0005,0201,0204,0402,0702,0B04,0B05,FF01", outClusters: "0003,000A,0019", model: "TH1123ZB-G2", manufacturer:"Sinope Technologies", deviceJoinName: "Sinope Thermostat TH1123ZB-G2"
        fingerprint profileId: "0104", endpointId: "01", inClusters: "0000,0003,0004,0005,0201,0204,0402,0702,0B04,0B05,FF01", outClusters: "000A,FF01,0019", model: "TH1124ZB", manufacturer: "Sinope Technologies", deviceJoinName: "Sinope Thermostat TH1124ZB"

	}
}


// Constants

import groovy.transform.Field
@Field static final Map constBacklightModes = [ "off": 0x0, "adaptive": 0x1, "on": 0x1,
                                               0x0: "off", 0x1: "on" ]
@Field static final Map constBacklightModesG2 = [ "off": 0x2, "adaptive": 0x0, "on": 0x1,
                                                 0x2: "off", 0x0: "adaptive", 0x1: "on" ]
@Field static final Map constSecondTempDisplayModes =  [ 0x0 : "auto", 0x01: "setpoint", 0x02: "outdoor",
                                                        "auto": 0x0, "setpoint": 0x1, "outdoor": 0x2 ]


//-- Installation ----------------------------------------------------------------------------------------
def installed() {
	if (txtEnable) log.info "installed() : running configure()"
	configure()
	refresh()
}

def initialize() {
	if (txtEnable) log.info "installed() : running configure()"
	configure()
	refresh()
}

def updated() {
	if (txtEnable) log.info "updated() : running configure()"
	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
		state.updatedLastRanAt = now()
		configure()
		refresh()
	}
}

def uninstalled() {
	if (txtEnable) log.info "uninstalled() : unscheduling configure() and reset()"
	try {
		unschedule()
	} catch (errMsg) {
		log.info "uninstalled(): Error unschedule() - ${errMsg}"
	}
}


//-- Parsing -----------------------------------------------------------------------------------------

// parse events into attributes
def parse(String description) {
	if (txtEnable) log.debug "parse - description = ${description}"
	def result = []
	def cluster = zigbee.parse(description)
	if (description?.startsWith("read attr -")) {
		// log.info description
		def descMap = zigbee.parseDescriptionAsMap(description)
		result += createCustomMap(descMap)
		if(descMap.additionalAttrs){
			def mapAdditionnalAttrs = descMap.additionalAttrs
			mapAdditionnalAttrs.each{add ->
			add.cluster = descMap.cluster
			result += createCustomMap(add)
			}
		}
	}
	return result
}

private createCustomMap(descMap){
	def result = null
	def map = [: ]
	if (descMap.cluster == "0201" && descMap.attrId == "0000") {
		map.name = "temperature"
		map.value = getTemperature(descMap.value)

	} else if (descMap.cluster == "0201" && descMap.attrId == "0008") {
		map.name = "thermostatOperatingState"
		map.value = getHeatingDemand(descMap.value)
		def heatingDemandValue = map.value.toInteger()
		if (device.currentValue("maxPower") != null) {
			def maxPowerValue = device.currentValue("maxPower").toInteger()
			def powerValue = Math.round(maxPowerValue*heatingDemandValue/100)
			sendEvent(name: "power", value: powerValue, unit: "W")
		}
		sendEvent(name: "heatingDemand", value: heatingDemandValue, unit: "%")
		map.value = (map.value.toInteger() < 5) ? "idle" : "heating"

	} else if (descMap.cluster == "0201" && descMap.attrId == "0012") {
		map.name = "heatingSetpoint"
		map.value = getTemperature(descMap.value)
		sendEvent(name: "thermostatSetpoint", value: map.value, unit: getTemperatureScale()) //For interoperability with SharpTools

	} else if (descMap.cluster == "0201" && descMap.attrId == "001C") {
		map.name = "thermostatMode"
		map.value = getModeMap()[descMap.value]

	} else if (descMap.cluster == "0204" && descMap.attrId == "0001") {
		map.name = "lock"
		map.value = getLockMap()[descMap.value]

	} else if (descMap.cluster == "0B04" && descMap.attrId == "050B") {
		map.name = "power"
		map.value = getActivePower(descMap.value)
		map.unit = "W"

	} else if (descMap.cluster == "0B04" && descMap.attrId == "050D") {
		map.name = "maxPower"
		map.value = getActivePower(descMap.value)
		map.unit = "W"

	} else if (descMap.cluster == "0702" && descMap.attrId == "0000") {
		state.energyValue = getEnergy(descMap.value) as BigInteger
		runIn(2,"energyCalculation")
	}

	if (map) {
		def isChange = isStateChange(device, map.name, map.value.toString())
		map.displayed = isChange
		if ((map.name.toLowerCase().contains("temp")) || (map.name.toLowerCase().contains("setpoint"))) {
			map.scale = scale
		}
		result = createEvent(map)
	}
    return result
}

//-- Capabilities -----------------------------------------------------------------------------------------

def configure(){
	if (txtEnable) log.info "configure()"
	// Set unused default values
	sendEvent(name: "coolingSetpoint", value:getTemperature("0BB8")) // 0x0BB8 =  30 Celsius
	sendEvent(name: "thermostatFanMode", value:"auto") // We dont have a fan, so auto it is
	updateDataValue("lastRunningMode", "heat") // heat is the only compatible mode for this device
	sendEvent(name: "supportedThermostatModes", value:  "[\"off\", \"heat\"]") //We set the supported thermostat mode
    	sendEvent(name: "supportedThermostatFanModes", value:  "[\"auto\"]") //We set the supported thermostat mode

	try
	{
		unschedule()
	}
	catch (e)
	{
	}

	int timesec = Math.abs( new Random().nextInt() % 59)
	int timemin = Math.abs( new Random().nextInt() % 59)
	int timehour = Math.abs( new Random().nextInt() % 2)
	schedule(timesec + " " + timemin + " " + timehour + "/3 * * ? *",refreshTime) //refresh the clock at random begining and then every 3h
	schedule("0 0 * * * ? *", energySecCalculation)
	schedule("0 0 0 * * ? *", resetDailyEnergy)
	schedule("0 0 0 1 * ? *", resetMonthlyEnergy)
	timesec = Math.abs( new Random().nextInt() % 59)
	timemin = Math.abs( new Random().nextInt() % 59)
	timehour = Math.abs( new Random().nextInt() % 23)
	schedule(timesec + " " + timemin + " " + timehour + " * * ? *", refreshMaxPower) //refresh maximum power capacity of the equipement wired to the thermostat one time per day at a random moment

    if (weeklyReset == null)
        weeklyReset = "Sunday" as String
    if (yearlyReset == null)
        yearlyReset = "January" as String

    switch (yearlyReset) {
        case "January" :
            schedule("0 0 0 1 1 ? *", resetYearlyEnergy)
            break
        case "February" :
            schedule("0 0 0 1 2 ? *", resetYearlyEnergy)
            break
        case "March" :
            schedule("0 0 0 1 3 ? *", resetYearlyEnergy)
            break
        case "April" :
            schedule("0 0 0 1 4 ? *", resetYearlyEnergy)
            break
        case "May" :
            schedule("0 0 0 1 5 ? *", resetYearlyEnergy)
            break
        case "June" :
            schedule("0 0 0 1 6 ? *", resetYearlyEnergy)
            break
        case "July" :
            schedule("0 0 0 1 7 ? *", resetYearlyEnergy)
            break
        case "August" :
            schedule("0 0 0 1 8 ? *", resetYearlyEnergy)
            break
        case "September" :
            schedule("0 0 0 1 9 ? *", resetYearlyEnergy)
            break
        case "October" :
            schedule("0 0 0 1 10 ? *", resetYearlyEnergy)
            break
        case "November" :
            schedule("0 0 0 1 11 ? *", resetYearlyEnergy)
            break
        case "December" :
            schedule("0 0 0 1 12 ? *", resetYearlyEnergy)
            break
    }

    switch (weeklyReset) {
        case "Sunday" :
            schedule("0 0 0 ? * 1 *", resetWeeklyEnergy)
            break
        case "Monday" :
            schedule("0 0 0 ? * 2 *", resetWeeklyEnergy)
            break
        case "Tuesday" :
            schedule("0 0 0 ? * 3 *", resetWeeklyEnergy)
            break
        case "Wednesday" :
            schedule("0 0 0 ? * 4 *", resetWeeklyEnergy)
            break
        case "Thursday" :
            schedule("0 0 0 ? * 5 *", resetWeeklyEnergy)
            break
        case "Friday" :
            schedule("0 0 0 ? * 6 *", resetWeeklyEnergy)
            break
        case "Saturday" :
            schedule("0 0 0 ? * 7 *", resetWeeklyEnergy)
            break
    }

	// Prepare our zigbee commands
	def cmds = []

	// Configure Reporting
	if (tempChange == null)
		tempChange = 50 as int
	if (HeatingChange == null)
		HeatingChange = 5 as int
	if (energyChange == null)
		energyChange = 10 as int

	cmds += zigbee.configureReporting(0x0201, 0x0000, 0x29, 30, 580, (int) tempChange)  //local temperature
	cmds += zigbee.configureReporting(0x0201, 0x0008, 0x0020, 59, 590, (int) HeatingChange) //PI heating demand
	cmds += zigbee.configureReporting(0x0702, 0x0000, DataType.UINT48, 59, 1799, (int) energyChange) //Energy reading
	cmds += zigbee.configureReporting(0x0201, 0x0012, 0x0029, 15, 302, 40)   //occupied heating setpoint
	cmds += zigbee.configureReporting(0x0204, 0x0000, 0x30, 1, 0)            //temperature display mode
	cmds += zigbee.configureReporting(0x0204, 0x0001, 0x30, 1, 0)            //keypad lockout


	// Configure displayed scale
	if (getTemperatureScale() == 'C') {
		cmds += zigbee.writeAttribute(0x0204, 0x0000, 0x30, 0)    // Wr °C on thermostat display
	} else {
		cmds += zigbee.writeAttribute(0x0204, 0x0000, 0x30, 1)    // Wr °F on thermostat display
	}

    // Configure display mode
    runIn(1, "setBacklightMode")

    // Configure secondary display
    runIn(1, "setSecondTempDisplay")

    // Configure Outdoor Weather parameters
    cmds += zigbee.writeAttribute(0xFF01, 0x0011, DataType.UINT16, 10800, [mfgCode: "0x119C"])  //set the outdoor temperature timeout to 3 hours

	//Configure Clock Format
	if (prefTimeFormatParam == null)
		prefTimeFormatParam == "24h" as String
	if (prefTimeFormatParam == "12h AM/PM") {//12h AM/PM "24h"
		if(txtEnable) log.info "Set to 12h AM/PM"
		cmds += zigbee.writeAttribute(0xFF01, 0x0114, 0x30, 0x0001)
	} else {//24h
		if(txtEnable) log.info "Set to 24h"
		cmds += zigbee.writeAttribute(0xFF01, 0x0114, 0x30, 0x0000)
	}

	if (cmds)
		sendZigbeeCommands(cmds) // Submit zigbee commands
	return
}

def resetEnergyOffset(text) {
    if (text != null) {
	    BigInteger newOffset = text.toBigInteger()
	    state.dailyEnergy = state.dailyEnergy - state.offsetEnergy + newOffset
	    state.weeklyEnergy = state.weeklyEnergy - state.offsetEnergy + newOffset
	    state.monthlyEnergy = state.monthlyEnergy - state.offsetEnergy + newOffset
        state.yearlyEnergy = state.yearlyEnergy - state.offsetEnergy + newOffset
	    state.offsetEnergy = newOffset
        float totalEnergy = (state.energyValue + state.offsetEnergy)/1000
        sendEvent(name: "energy", value: totalEnergy, unit: "kWh")
	    runIn(2,energyCalculation)
	    runIn(2,energySecCalculation)
    }
}

def energyCalculation() {
	if (state.offsetEnergy == null)
		state.offsetEnergy = 0 as BigInteger
	if (state.dailyEnergy == null)
		state.dailyEnergy = state.energyValue as BigInteger
	if (device.currentValue("energy") == null)
		sendEvent(name: "energy", value: 0, unit: "kWh")
	if (energyPrice == null)
		energyPrice = 9.38 as float

	if (state.energyValue + state.offsetEnergy < device.currentValue("energy")*1000) { //Although energy are parse as BigInteger, sometimes (like 1 times per month during heating  time) the value received is lower than the precedent but not zero..., so we define a new offset when that happen
		BigInteger newOffset = device.currentValue("energy")*1000 - state.energyValue as BigInteger
		if (newOffset < 1e10) //Sometimes when the hub boot, the offset is very large... too large
		    state.offsetEnergy = newOffset
	    }

	float dailyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.dailyEnergy)/1000)
	float totalEnergy = (state.energyValue + state.offsetEnergy)/1000
	localCostPerKwh = energyPrice as float
	float dailyCost = roundTwoPlaces(dailyEnergy*localCostPerKwh/100)

	sendEvent(name: "dailyEnergy", value: dailyEnergy, unit: "kWh")
	sendEvent(name: "dailyCost", value: dailyCost, unit: "\$")
	sendEvent(name: "energy", value: totalEnergy, unit: "kWh")
}

def energySecCalculation() { //This one is performed every hour to not overwhelm the number of events which will create a warning in hubitat main page
	if (state.weeklyEnergy == null)
		state.weeklyEnergy = state.energyValue as BigInteger
	if (state.monthlyEnergy == null)
		state.monthlyEnergy = state.energyValue as BigInteger
	if (state.yearlyEnergy == null)
		state.yearlyEnergy = state.energyValue as BigInteger
	if (energyPrice == null)
		energyPrice = 9.38 as float

	float weeklyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.weeklyEnergy)/1000)
	float monthlyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.monthlyEnergy)/1000)
	float yearlyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.yearlyEnergy)/1000)
	float totalEnergy = (state.energyValue + state.offsetEnergy)/1000

	localCostPerKwh = energyPrice as float
	float weeklyCost = roundTwoPlaces(weeklyEnergy*localCostPerKwh/100)
	float monthlyCost = roundTwoPlaces(monthlyEnergy*localCostPerKwh/100)
	float yearlyCost = roundTwoPlaces(yearlyEnergy*localCostPerKwh/100)
	float totalCost = roundTwoPlaces(totalEnergy*localCostPerKwh/100)

	sendEvent(name: "weeklyEnergy", value: weeklyEnergy, unit: "kWh")
	sendEvent(name: "monthlyEnergy", value: monthlyEnergy, unit: "kWh")
	sendEvent(name: "yearlyEnergy", value: yearlyEnergy, unit: "kWh")

	sendEvent(name: "weeklyCost", value: weeklyCost, unit: "\$")
	sendEvent(name: "monthlyCost", value: monthlyCost, unit: "\$")
	sendEvent(name: "yearlyCost", value: yearlyCost, unit: "\$")
	sendEvent(name: "cost", value: totalCost, unit: "\$")

}

def resetDailyEnergy() {
	state.dailyEnergy = state.energyValue + state.offsetEnergy
    if (energyPrice == null)
        energyPrice = 9.38 as float
    localCostPerKwh = energyPrice as float
    float dailyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.dailyEnergy)/1000)
	float dailyCost = roundTwoPlaces(dailyEnergy*localCostPerKwh/100)
    sendEvent(name: "dailyEnergy", value: dailyEnergy, unit: "kWh")
	sendEvent(name: "dailyCost", value: dailyCost, unit: "\$")

}

def resetWeeklyEnergy() {
	state.weeklyEnergy = state.energyValue + state.offsetEnergy
    if (energyPrice == null)
        energyPrice = 9.38 as float
    localCostPerKwh = energyPrice as float
    float weeklyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.weeklyEnergy)/1000)
	float weeklyCost = roundTwoPlaces(weeklyEnergy*localCostPerKwh/100)
    sendEvent(name: "weeklyEnergy", value: weeklyEnergy, unit: "kWh")
	sendEvent(name: "weeklyCost", value: weeklyCost, unit: "\$")
}

def resetMonthlyEnergy() {
	state.monthlyEnergy = state.energyValue + state.offsetEnergy
    if (energyPrice == null)
        energyPrice = 9.38 as float
    localCostPerKwh = energyPrice as float
    float monthlyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.monthlyEnergy)/1000)
	float monthlyCost = roundTwoPlaces(monthlyEnergy*localCostPerKwh/100)
    sendEvent(name: "monthlyEnergy", value: monthlyEnergy, unit: "kWh")
	sendEvent(name: "monthlyCost", value: monthlyCost, unit: "\$")
}

def resetYearlyEnergy() {
	state.yearlyEnergy = state.energyValue + state.offsetEnergy
    if (energyPrice == null)
        energyPrice = 9.38 as float
    localCostPerKwh = energyPrice as float
    float yearlyEnergy = roundTwoPlaces((state.energyValue + state.offsetEnergy - state.yearlyEnergy)/1000)
	float yearlyCost = roundTwoPlaces(yearlyEnergy*localCostPerKwh/100)
    sendEvent(name: "yearlyEnergy", value: yearlyEnergy, unit: "kWh")
	sendEvent(name: "yearlyCost", value: yearlyCost, unit: "\$")
}

def refresh() {
	if (txtEnable) log.info "refresh()"
	runIn(2,"refreshTime")
	def cmds = []
	cmds += zigbee.readAttribute(0x0B04, 0x050D) //Read highest power delivered
	cmds += zigbee.readAttribute(0x0201, 0x0000) //Read Local Temperature
	cmds += zigbee.readAttribute(0x0201, 0x0008) //Read PI Heating State
	cmds += zigbee.readAttribute(0x0201, 0x0012) //Read Heat Setpoint
	cmds += zigbee.readAttribute(0x0201, 0x001C) //Read System Mode
	cmds += zigbee.readAttribute(0x0204, 0x0000) //Read Temperature Display Mode
	cmds += zigbee.readAttribute(0x0204, 0x0001) //Read Keypad Lockout
	cmds += zigbee.readAttribute(0x0702, 0x0000) //Read energy delivered

	if (cmds)
		sendZigbeeCommands(cmds) // Submit zigbee commands
}

def refreshTemp() {
	def cmds = []
	cmds += zigbee.readAttribute(0x0201, 0x0000)  //Read Local Temperature

	if (cmds)
		sendZigbeeCommands(cmds)
}

def refreshMaxPower() {
	def cmds = []
	cmds += zigbee.readAttribute(0x0B04, 0x050D)  //Read highest power delivered

	if (cmds)
		sendZigbeeCommands(cmds)
}

def refreshTime() {
	def cmds=[]
	// Time
	def thermostatDate = new Date();
	def thermostatTimeSec = thermostatDate.getTime() / 1000;
	def thermostatTimezoneOffsetSec = thermostatDate.getTimezoneOffset() * 60;
	int currentTimeToDisplay = Math.round(thermostatTimeSec - thermostatTimezoneOffsetSec - 946684800); //time from 2000-01-01 00:00
	cmds += zigbee.writeAttribute(0xFF01, 0x0020, DataType.UINT32, currentTimeToDisplay, [mfgCode: "0x119C"])

	if (cmds)
		sendZigbeeCommands(cmds)
}

def setClockTime() {
    refreshTime()
}

def displayOn() {
    setBacklightMode("on")
}

def displayOff() {
    setBacklightMode("off")
}

def displayAdaptive() {
    setBacklightMode("adaptive")
}

def auto() {
	log.warn "auto(): mode is not available for this device. => Defaulting to heat mode instead."
	heat()
}

def cool() {
	log.warn "cool(): mode is not available for this device. => Defaulting to heat mode instead."
	heat()
}

def emergencyHeat() {
	log.warn "emergencyHeat(): mode is not available for this device. => Defaulting to heat mode instead."
	heat()
}

def fanAuto() {
	log.warn "fanAuto(): mode is not available for this device"
}

def fanCirculate() {
	log.warn "fanCirculate(): mode is not available for this device"
}

def fanOn() {
	log.warn "fanOn(): mode is not available for this device"
}

def heat() {
	if (txtEnable) log.info "heat(): mode set"

	def cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 4)
	//cmds += zigbee.readAttribute(0x0201, 0x0008)
	cmds += zigbee.readAttribute(0x0201, 0x001C)

	// Submit zigbee commands
	sendZigbeeCommands(cmds)
}

def off() {
	if (txtEnable) log.info "off(): mode set, it means no heating!"

	def cmds = []
	cmds += zigbee.writeAttribute(0x0201, 0x001C, 0x30, 0)
	//cmds += zigbee.readAttribute(0x0201, 0x0008)
	cmds += zigbee.readAttribute(0x0201, 0x001C)

	// Submit zigbee commands
	sendZigbeeCommands(cmds)
}

def setCoolingSetpoint(degrees) {
	log.warn "setCoolingSetpoint(${degrees}): is not available for this device"
}

def setHeatingSetpoint(preciseDegrees) {
	if (preciseDegrees != null) {
		unschedule("Setpoint")
		state.setPoint = preciseDegrees
		runIn(2,"Setpoint")
	}
}

def Setpoint() {
	if (state.setPoint != device.currentValue("heatingSetpoint")) {
		runIn(30, "Setpoint")
		def temperatureScale = getTemperatureScale()
		def degrees = state.setPoint
		def cmds = []

		if (txtEnable) log.info "setHeatingSetpoint(${degrees}:${temperatureScale})"

		def celsius = (temperatureScale == "C") ? degrees as Float : (fahrenheitToCelsius(degrees) as Float).round(2)
		int celsius100 = Math.round(celsius * 100)

		cmds += zigbee.writeAttribute(0x0201, 0x0012, 0x29, celsius100) //Write Heat Setpoint

		// Submit zigbee commands
		sendZigbeeCommands(cmds)
	}
}

def setThermostatFanMode(fanmode){
	log.warn "setThermostatFanMode(${fanmode}): is not available for this device"
}

def setThermostatMode(String value) {
	if (txtEnable) log.info "setThermostatMode(${value})"

	switch (value) {
		case "heat":
		case "emergency heat":
		case "auto":
		case "cool":
			return heat()

		case "off":
			return off()
	}
}

def unlock() {
	if (txtEnable) log.info "TH1123ZB >> unlock()"
	sendEvent(name: "lock", value: "unlocked")

	def cmds = []
	cmds += zigbee.writeAttribute(0x0204, 0x0001, DataType.ENUM8, 0x00)

	sendZigbeeCommands(cmds)
}

def lock() {
	if (txtEnable) log.info "TH1123ZB >> lock()"
	sendEvent(name: "lock", value: "locked")

	def cmds = []
	cmds += zigbee.writeAttribute(0x0204, 0x0001, DataType.ENUM8, 0x01)

	sendZigbeeCommands(cmds)
}

def deviceNotification(text) {
    if (text != null) {
        double outdoorTemp = text.toDouble()

        log.debug "deviceNotification() : Received outdoor weather report : ${outdoorTemp} ${getTemperatureScale()}"
        sendEvent(name: "outdoorTemp", value: outdoorTemp, unit: getTemperatureScale())

        // the value sent to the thermostat must be in C
        // TODO: check if this is necessary - thermostat and hub should be configured the same...
        if (getTemperatureScale() == 'F') {
            outdoorTemp = fahrenheitToCelsius(outdoorTemp).toDouble()
        }

        def int outdoorTempDevice = outdoorTemp*100  // device expects hundredths
        def cmds = []
        cmds += zigbee.writeAttribute(0xFF01, 0x0010, DataType.INT16, outdoorTempDevice, [mfgCode: "0x119C"]) //set the outdoor temperature as integer

        // Submit zigbee commands
        sendZigbeeCommands(cmds)
    }
}

//-- Private functions -----------------------------------------------------------------------------------
private void sendZigbeeCommands(cmds) {
	cmds.removeAll { it.startsWith("delay") }
	def hubAction = new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE)
	sendHubCommand(hubAction)
}

private getTemperature(value) {
	if (value != null) {
		def celsius = Integer.parseInt(value, 16) / 100
		if (getTemperatureScale() == "C") {
			return celsius
		}
		else {
			return Math.round(celsiusToFahrenheit(celsius))
		}
	}
}

private getModeMap() {
	[
		"00": "off",
		"04": "heat"
	]
}

private getLockMap() {
	[
		"00": "unlocked ",
		"01": "locked ",
	]
}

private getActivePower(value) {
	if (value != null) {
	def activePower = Integer.parseInt(value, 16)
	return activePower
	}
}

private getEnergy(value) {
	if (value != null) {
		BigInteger EnergySum = new BigInteger(value,16)
		return EnergySum
	}
}

private getTemperatureScale() {
	return "${location.temperatureScale}"
}

private getHeatingDemand(value) {
	if (value != null) {
		def demand = Integer.parseInt(value, 16)
		return demand.toString()
	}
}

private roundTwoPlaces(val) {
	return Math.round(val * 100) / 100
}

private hex(value) {
	String hex = new BigInteger(Math.round(value).toString()).toString(16)
	return hex
}

private setBacklightMode(mode = prefBacklightMode) {
    def backlightModeAttr = null
    if (isG2Model()) {
        backlightModeAttr = constBacklightModesG2[mode]
    } else {
        backlightModeAttr = constBacklightModes[mode]
    }
    if (backlightModeAttr == null) {
        log.warn("invalid display mode ${mode}")
        return
    }

    log.debug("setting display backlight to ${mode} (${backlightModeAttr})")
    device.updateSetting("prefBacklightMode",[value: mode, type: "enum"])
    def cmds = []
    cmds += zigbee.writeAttribute(0x0201, 0x0402, DataType.ENUM8, backlightModeAttr, [mfgCode: "0x119C"])
    sendZigbeeCommands(cmds) // Submit zigbee commands
}

private setSecondTempDisplay(mode = prefSecondTempDisplay) {
    def BigInteger secondDisplaySetting = constSecondTempDisplayModes[mode]
    if (secondDisplaySetting != null) {
        log.debug("setting secondary temperature display to ${mode} (${secondDisplaySetting})")
        device.updateSetting("prefSecondTempDisplay",[value: mode, type: "enum"])
        def cmds = []
        cmds += zigbee.writeAttribute(0xFF01, 0x0012, DataType.ENUM8, secondDisplaySetting, [mfgCode: "0x119C"])
        sendZigbeeCommands(cmds) // Submit zigbee commands
    } else {
        log.warn("invalid secondary temperature display mode ${mode}")
    }
}

private isG2Model() {
    device.getDataValue("model").contains("-G2")
}
