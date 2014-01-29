package model.team;

import simulator.Range;

public enum Duration {
	/* Via Dr. Goodrich */
																				//1 - Critical to safety, 2 - critical to mission success, 3 - discretionary
//	NEW_SEARCH_AOI(120,600),													//3
//	NEW_TARGET_DESCRIPTION(120,600),											//3
//	START_SEARCH(5,10),															//3
//	TERMINATE_SEARCH(5,10),	//HAVE TO PLAN ROUTE HOME: 120-600					//2
//	TARGET_SIGHTED_T(60,300),													//2
//	TARGET_SIGHTED_F(60,300),													//3
//	VIDEO_SIGNAL_LOST(60,300),													//2
//	LOW_BATTERY(5,10),	//HAVE TO PLAN ROUTE HOME: 120-600						//1
//	BATTERY_DIED(999999999),													//1
//	LOW_HAG(10,300),															//1
//	CRASHED_HAG(999999999),														//1
//	SIGNAL_LOST(10,600),														//2
//	SIGNAL_RESTORED(5,10),	//PLAN A NEW PATH TO MAINTAIN SIGNAL: 120-600		//2
	
	
	/* Parent Search Durations */
	
	PS_SEND_DATA_PS(3),
	PS_POKE_MM(10), 
	PS_TX_DATA_MM(5),
	
	/* Mission Manager Durations */
	
	MM_POKE_PS(10),
	MM_POKE_TO_TX_PS(5),
	MM_RX_PS(22),
	MM_POKE_VO(10),
	MM_POKE_OP(10),
	MM_TX_OP(10),
	MM_OBSERVING_VGUI(10),
	MM_TX_VGUI(10),
	MM_TO_IDLE(10),
	MM_TX_PS(10),
	MM_TX_VO(10),
	
	/* Video Operator Durations */

	VO_RX_MM(20),
	VO_TX_MM(10),
	
	/* Video Operator Gui Durations */
	
	/* UAV Operator Durations */
	
	OP_TX_MM(10),
	OP_TX_OGUI(10),
	OP_RX_MM(22),
	
	/* UAV Operator Gui Durations */
	
	/* UAV Durations */
	
	UAV_ADJUST_PATH(60),
	UAV_LANDING(10),
	UAV_TAKE_OFF(10),
	
	/* UAV Battery Durations */

	UAVBAT_ACTIVE_TO_LOW(30),
	UAVBAT_LOW_TO_DEAD(100),
	UAVBAT_DURATION(3600),
	
	/* General */
	
	ACK(1),
	NEXT(1),
	POKE(10),
	OP_OBSERVE_GUI(10),
	OP_OBSERVE_UAV(5,10),
	OP_POST_FLIGHT_COMPLETE(10),
	RANDOM(50),
	PS_TX_MM(10),
	PS_RX_MM(10),
	UAV_PATH_DUR(100);
	
	private Integer _minimum;
	private Integer _maximum;
	private Range _range;

	/**
	 * creates a bounded duration
	 * @param minimum represents the minimum bound of the duration in seconds
	 * @param maximum represents the maximum bound of the duration in seconds
	 */
	Duration(int minimum, int maximum) {
		_minimum = minimum;
		_maximum = maximum;
		_range = new Range(_minimum, _maximum);
	}
	
	/**
	 * creates a fixed duration
	 * @param duration represents the fixed time duration in seconds
	 */
	Duration(Integer duration){
		_minimum = duration;
		_maximum = duration;
		_range = new Range(_minimum, _maximum);
	}
	
	/**
	 * updates the _min and _max
	 * @param duration represents the integer value of the duration
	 */
	public Duration update(Integer duration){
		_minimum = duration;
		_maximum = duration;
		_range = new Range(_minimum, _maximum);
		return this;
	}

	/**
	 * works like a normal toString methods 
	 * @return return a string representation of the duration
	 */
	public String toString() {
		String result = "";
		
		if (_minimum == _maximum) {
			result = Integer.toString(_maximum);
		} else {
			result = "(" + Integer.toString(_minimum) + "-" + Integer.toString(_maximum) + ")";
		}
		
		return result;
	}

	public Range getRange() {
//		if(_minimum == _maximum)
//			return _maximum;
//		else{
//			Random rand = new Random();
//			int dur = _minimum + rand.nextInt(_maximum-_minimum);
//			return dur;
//		}
		return _range;
	}

}
