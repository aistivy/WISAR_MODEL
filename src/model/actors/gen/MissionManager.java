package model.actors.gen;

import model.actors.*;
import model.team.*;
import simulator.*;

public class MissionManager extends Actor {
public enum DATA_MM_MM_COMM{
	MM_START_WAVE_MM__MM_STOP_PEP_TALK_MM,
	MM_START_PEP_TALK_MM__MM_STOP_PEP_TALK_MM,
	MM_STOP_WAVE_MM__MM_START_SPEAK_MM,
	MM_STOP_WAVE_MM,
	MM_STOP_SPEAK_MM,
}
public enum VIDEO_MM_OP_COMM{
	MM_WAVE_OP,
	MM_SAD_OP,
}
public enum AUDIO_MM_OP_COMM{
	MM_HELLO_OP,
}
public MissionManager(ComChannelList inputs, ComChannelList outputs) {
	setName("MissionManager");
	State DONE = new State("DONE");
	State LEAVE = new State("LEAVE");
	State SPEAK = new State("SPEAK");
	State WAVE = new State("WAVE");
	State IDLE = new State("IDLE");
	initializeInternalVariables();
	initializeLEAVE(inputs, outputs, LEAVE, DONE);
	initializeIDLE(inputs, outputs, IDLE, WAVE);
	initializeWAVE(inputs, outputs, IDLE, WAVE, SPEAK);
	initializeSPEAK(inputs, outputs, SPEAK, LEAVE);
	startState(IDLE);
}
 public void initializeLEAVE(ComChannelList inputs, ComChannelList outputs, State LEAVE, State DONE) {
	// (LEAVE,[],[],0,NEXT,1.0)x(DONE,[],[])
	LEAVE.add(new Transition(_internal_vars, inputs, outputs, DONE, Duration.NEXT.getRange(), 0, 1.0) {
		@Override
		public boolean isEnabled() { 
			return true;
		}
	}); // in comments
	add(LEAVE);
}
 public void initializeIDLE(ComChannelList inputs, ComChannelList outputs, State IDLE, State WAVE) {
	// (IDLE,[],[DESIRE>5],0,[2-5],1.0)X(WAVE,[V=MM_WAVE_OP,D=MM_START_WAVE_MM__MM_STOP_PEP_TALK_MM],[])
	IDLE.add(new Transition(_internal_vars, inputs, outputs, WAVE, new Range(2, 5), 0, 1.0) {
		@Override
		public boolean isEnabled() { 
			if(_internal_vars.getVariable("DESIRE") instanceof Integer && new Integer(5) >= (Integer) _internal_vars.getVariable ("DESIRE")) {
				return false;
			}
			setTempOutput(Channels.VIDEO_MM_OP_COMM.name(), MissionManager.VIDEO_MM_OP_COMM.MM_WAVE_OP);
			setTempOutput(Channels.DATA_MM_MM_COMM.name(), MissionManager.DATA_MM_MM_COMM.MM_START_WAVE_MM__MM_STOP_PEP_TALK_MM);
			return true;
		}
	}); // in comments
	// (IDLE,[],[DESIRE<=5],0,[5-10],1.0)X(IDLE,[D=MM_START_PEP_TALK_MM__MM_STOP_PEP_TALK_MM],[DESIRE=++])
	IDLE.add(new Transition(_internal_vars, inputs, outputs, IDLE, new Range(5, 10), 0, 1.0) {
		@Override
		public boolean isEnabled() { 
			if(_internal_vars.getVariable("DESIRE") instanceof Integer && new Integer(5) < (Integer) _internal_vars.getVariable ("DESIRE")) {
				return false;
			}
			setTempOutput(Channels.DATA_MM_MM_COMM.name(), MissionManager.DATA_MM_MM_COMM.MM_START_PEP_TALK_MM__MM_STOP_PEP_TALK_MM);
			setTempInternalVar("DESIRE", (Integer)_internal_vars.getVariable("DESIRE") + 1);
			return true;
		}
	}); // in comments
	add(IDLE);
}
 public void initializeWAVE(ComChannelList inputs, ComChannelList outputs, State IDLE, State WAVE, State SPEAK) {
	// (WAVE,[V=OP_WAVE_MM],[],0,[2-5],1.0)X(SPEAK,[A=MM_HELLO_OP,D=MM_STOP_WAVE_MM__MM_START_SPEAK_MM],[DESIRE=++])
	WAVE.add(new Transition(_internal_vars, inputs, outputs, SPEAK, new Range(2, 5), 0, 1.0) {
		@Override
		public boolean isEnabled() { 
			if(!Operator.VIDEO_OP_MM_COMM.OP_WAVE_MM.equals(_inputs.get(Channels.VIDEO_OP_MM_COMM.name()).getValue())) {
				return false;
			}
			setTempOutput(Channels.AUDIO_MM_OP_COMM.name(), MissionManager.AUDIO_MM_OP_COMM.MM_HELLO_OP);
			setTempOutput(Channels.DATA_MM_MM_COMM.name(), MissionManager.DATA_MM_MM_COMM.MM_STOP_WAVE_MM__MM_START_SPEAK_MM);
			setTempInternalVar("DESIRE", (Integer)_internal_vars.getVariable("DESIRE") + 1);
			return true;
		}
	}); // in comments
	// (WAVE,[],[],0,[5-10],1.0)X(IDLE,[V=MM_SAD_OP,D=MM_STOP_WAVE_MM],[DESIRE=--])
	WAVE.add(new Transition(_internal_vars, inputs, outputs, IDLE, new Range(5, 10), 0, 1.0) {
		@Override
		public boolean isEnabled() { 
			setTempOutput(Channels.VIDEO_MM_OP_COMM.name(), MissionManager.VIDEO_MM_OP_COMM.MM_SAD_OP);
			setTempOutput(Channels.DATA_MM_MM_COMM.name(), MissionManager.DATA_MM_MM_COMM.MM_STOP_WAVE_MM);
			setTempInternalVar("DESIRE", (Integer)_internal_vars.getVariable("DESIRE") - 1);
			return true;
		}
	}); // in comments
	add(WAVE);
}
 public void initializeSPEAK(ComChannelList inputs, ComChannelList outputs, State SPEAK, State LEAVE) {
	// (SPEAK,[A=OP_HELLO_MM],[],0,[2-5],1.0)X(LEAVE,[D=MM_STOP_SPEAK_MM],[DESIRE=--])
	SPEAK.add(new Transition(_internal_vars, inputs, outputs, LEAVE, new Range(2, 5), 0, 1.0) {
		@Override
		public boolean isEnabled() { 
			if(!Operator.AUDIO_OP_MM_COMM.OP_HELLO_MM.equals(_inputs.get(Channels.AUDIO_OP_MM_COMM.name()).getValue())) {
				return false;
			}
			setTempOutput(Channels.DATA_MM_MM_COMM.name(), MissionManager.DATA_MM_MM_COMM.MM_STOP_SPEAK_MM);
			setTempInternalVar("DESIRE", (Integer)_internal_vars.getVariable("DESIRE") - 1);
			return true;
		}
	}); // in comments
	add(SPEAK);
}
@Override
protected void initializeInternalVariables() {
	_internal_vars.addVariable("DESIRE", 0);
}
}