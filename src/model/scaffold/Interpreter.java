package model.scaffold;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import simulator.Simulator;

/**
 * Builds a team of actors from lists of transitions in the model/transitions package.
 * Transition := (CurrentState,[Channel=InputData*],[Memory{=||>||<...}InputValue*],Priority,Duration,Probability)x(NextState,[Channel=OutputData*],[Memory=OutputValue*])
 * CurrentState := Name
 * Name := string
 * Channel := ChannelType
 * ChannelType := a||v||d||e
 * InputData := Data
 * Data := Source_Name_Destination
 * Source := Name
 * Destination := Name
 * Memory := Name
 * InputValue := boolean||integer
 * Duration := Name||[integer,integer]
 * NextState := Name
 * OutputData := Data
 * OutputValue := boolean||integer||Operation
 * Operation := "++"||"--"
 */
public class Interpreter {
	public static void main(String[] args){

		File f = getJTRFolder();
		//each actor
		HashMap<String, ArrayList<String[]>> inputs_outputs = new HashMap<String, ArrayList<String[]>>();
		for(File file : f.listFiles()){
			try {
				if(!file.getName().equals("Operator.txt") && !file.getName().equals("MissionManager.txt") && !file.getName().equals("ParentSearch.txt") && !file.getName().equals("UAVBattery.txt") && !file.getName().equals("UAVFlightPlan.txt"))
					continue;
				StringBuilder memory = new StringBuilder();
				memory.append("\n@Override\nprotected void initializeInternalVariables() {");
				
				//get the actor name and check if it is valid
				String name = file.getName();
				name = name.substring(0, name.indexOf('.'));
				if(name.length() > 0){
//					inputs_outputs.put(name, ""); //initialize the entry with a empty string
					HashMap<String, String> initializers = new HashMap<String, String>();
					HashMap<String, String> enumerations = new HashMap<String, String>();
					ArrayList<String> states = new ArrayList<String>();
					parseJTRFile(file, memory, name, initializers,
							enumerations, states, inputs_outputs);
					
					StringBuilder body = new StringBuilder();
					StringBuilder constructor = new StringBuilder();
					generateConstructorAndBody(name, initializers, states,
							body, constructor);
					
					StringBuilder enums = new StringBuilder();
					for(Entry<String, String> enumeration : enumerations.entrySet()){
						 //* The first element of a channel array is the channel name.
						 //* The second element of a channel array is the channel type (must be "VISUAL", "AUDIO", or "DATA").
						 //* The third element of a channel array is the channel direction (aka "OUTPUT" or "INPUT").
						updateChannelLists(inputs_outputs, name, enumeration);
						enums.append(enumeration.getValue() + "\n}");
					}
					
					createJavaFile(file, constructor, memory, name, body, enums);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		ArrayList<String> channels = buildTeamClass(inputs_outputs);
		buildChannelsClass(channels);
	}

	/**
	 * Takes the enumerated classes in each class to generate the corresponding channel lists.
	 * @param inputs_outputs
	 * @param name
	 * @param enumeration
	 */
	private static void updateChannelLists(
			HashMap<String, ArrayList<String[]>> inputs_outputs, String name,
			Entry<String, String> enumeration) {
		ArrayList<String[]> channel_list = inputs_outputs.get(name);
		channel_list = (channel_list==null)?new ArrayList<String[]>():channel_list;
		String channel_name = enumeration.getKey();
		String channel_type = channel_name.substring(0, channel_name.indexOf('_'));
		String[] channel = new String[]{channel_name,channel_type,"OUTPUT"};
		if (!channel_list.contains(channel))
			channel_list.add(channel);
		inputs_outputs.put(name, channel_list);
		String target = channel_name.substring(
				channel_name.indexOf('_', channel_name.indexOf('_')+1)+1,channel_name.indexOf('_', channel_name.indexOf('_', channel_name.indexOf('_')+1)+1));
		String target_name = "MM".equals(target)?"MissionManager":
			"PS".equals(target)?"ParentSearch":
				"OP".equals(target)?"Operator":
					"UAV".equals(target)?"UAV":
						"VO".equals(target)?"VideoOperator":
							"OGUI".equals(target)?"OperatorGui":
								"UAVBAT".equals(target)?"UAVBattery":
									"UAVFP".equals(target)?"UAVFlightPLan":
										"UAVVF".equals(target)?"UAVVideoFeed":
											"VGUI".equals(target)?"VideoOperatorGui":
												"";
		channel_list = inputs_outputs.get(target_name);
		channel_list = (channel_list==null)?new ArrayList<String[]>():channel_list;
		channel = new String[]{channel_name,channel_type,"INPUT"};
		if (!channel_list.contains(channel))
			channel_list.add(channel);
		inputs_outputs.put(target_name, channel_list);
	}

	/**
	 * takes a given file and converts JTR lines into Java code
	 * @param file
	 * @param memory
	 * @param name
	 * @param initializers
	 * @param enumerations
	 * @param states
	 * @param inputs_outputs 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static void parseJTRFile(File file, StringBuilder memory,
			String name, HashMap<String, String> initializers,
			HashMap<String, String> enumerations, ArrayList<String> states, HashMap<String, ArrayList<String[]>> inputs_outputs)
			throws FileNotFoundException, IOException {
		Interpreter xml = new Interpreter();
		BufferedReader br = new BufferedReader(new FileReader(file));
		String line = br.readLine();
		while(line != null){
			line = line.trim();
			if(xml.correctFormat(line)){
				String[] transition_state = xml.parseJTR(line, memory, name, enumerations, states, inputs_outputs);
				String state = transition_state[0].substring(0, transition_state[0].indexOf('.')).trim();
				if(initializers.containsKey(state)){
					if(initializers.get(state).contains("State " + transition_state[1])){
						initializers.put(state, initializers.get(state) + "\n\t// " + line + transition_state[0]);
					}else
						initializers.put(state, "State " + transition_state[1] + ", " + initializers.get(state) + "\n\t// " + line + transition_state[0]);
				}else{
					initializers.put(state, "State " + state + ", State " + transition_state[1] + ") {" + "\n\t// " + line + transition_state[0]);
				}
			}else if(line.length() > 0 && line.startsWith("("))
				System.out.println("error with comment: " + line);
			line = br.readLine();
		}
		memory.append("\n}");
	}
	
	/**
	 * Creates a string of java code for both the constructor and the body of the class
	 * @param name	the name of the actor
	 * @param initializers	
	 * @param states
	 * @param body	the java code for the body of the class
	 * @param constructor	the java code for the constructor of the class
	 */
	private static void generateConstructorAndBody(String name,
			HashMap<String, String> initializers, ArrayList<String> states,
			StringBuilder body, StringBuilder constructor) {
		for(String state : states)
			constructor.insert(0, "\n\tState " + state + " = new State(\"" + state + "\");");
		constructor.append("\n\tinitializeInternalVariables();");
		for(Entry<String, String> transition : initializers.entrySet()){
//						constructor.insert(0, "\n\tState " + transition.getKey() + " = new State(\"" + transition.getKey() + "\");");
			String call_line = transition.getValue().split("\n")[0];
			String parameters = call_line.substring(call_line.indexOf("State"), call_line.indexOf(')'));
			parameters = parameters.replaceAll("State ", "");
			constructor.append("\n\tinitialize" + transition.getKey() + "(inputs, outputs, " + parameters + ");");
			body.append("\n public void initialize" + transition.getKey() + "(ComChannelList inputs, ComChannelList outputs, ");
			body.append(transition.getValue()); 
			body.append("\n\tadd(" + transition.getKey() + ");\n}");
		}
		constructor.insert(0, "\n\tsetName(\"" + name + "\");");
		constructor.insert(0,"\npublic " + name + "(ComChannelList inputs, ComChannelList outputs) {");
		constructor.append("\n\tstartState(" + states.get(0) + ");");
		constructor.append("\n}");
	}
	
	/**
	 * Generates a java file based off the JTR file
	 * @param file
	 * @param constructor
	 * @param memory
	 * @param name
	 * @param body
	 * @param enums
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	private static void createJavaFile(File file, StringBuilder constructor,
			StringBuilder memory, String name, StringBuilder body,
			StringBuilder enums) throws IOException, FileNotFoundException {
		File new_file = file.getParentFile().getParentFile();
		for(File temp : new_file.listFiles()){
			if(temp.getName().equals("actors")){
				new_file = temp;
				break;
			}
		}
		for(File temp : new_file.listFiles()){
			if(temp.getName().equals("gen")){
				new_file = temp;
				break;
			}
		}
		new_file = new File(new_file.getPath() + "/" + name + ".java");
		new_file.createNewFile();
		System.out.println(new_file.toPath());
		PrintWriter writer = new PrintWriter(new_file);
		writer.print("package model.actors.gen;\n\nimport model.actors.*;\nimport model.team.*;\nimport simulator.*;\n\npublic class " + name + " extends Actor {" + enums.toString() + constructor.toString() + body.toString() + memory.toString() + "\n}");
		writer.close();
	}
	
	/**
	 * Finds the desired JTR folder and returns it
	 * @return
	 */
	private static File getJTRFolder() {
		File f = null;
		try {
			f = new File(new File(Interpreter.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent());
		} catch (URISyntaxException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		for(File file : f.listFiles()){
			if(file.getName().equals("src"))
				f = file;
		}
		for(File file : f.listFiles()){
			if(file.getName().equals("model"))
				f = file;
		}
		for(File file : f.listFiles()){
			if(file.getName().equals("scaffold"))
				f = file;
		}
//		for(File file : f.listFiles()){
//			if(file.getName().equals("comments"))
//				f = file;
//		}
		return f;
	}
	
	/**
	 * takes a comment line and converts it into the source code of the corresponding transition
	 * Transition := (CurrentState,[Channel=InputData*],[Memory{=||>||<...}InputValue*],Priority,Duration,Probability)x(NextState,[Channel=OutputData*],[Memory=OutputValue*])
	 * @param s
	 * @param memory
	 * @param name String identifier for the class
	 * @param enumerations mapping of all enumerations defined within the class
	 * @param inputs_outputs 
	 * @return the source code and the end state to be added into the file itself
	 */
	public String[] parseJTR(String s, StringBuilder memory, String name, HashMap<String,String> enumerations, ArrayList<String> states, HashMap<String, ArrayList<String[]>> inputs_outputs){
//		if("(IDLE,[E=NEW_SEARCH_AREA_EVENT],[SEARCH_ACTIVE=FALSE],1,NEXT,1.0)x(POKE_MM,[D=PS_START_TRANSMIT_AOI_PS,A=PS_POKE_MM],[SEARCH_ACTIVE=TRUE,NEW_SEARCH_AOI=++])".equals(s))
//			System.out.println(s);
		StringBuilder transition = new StringBuilder();
		//method call
		int start = s.indexOf('(')+1;
		int end = s.indexOf(',');
		String startState = s.substring(start, end);
		transition.append("\n\t" + startState + ".add(new Transition(_internal_vars, inputs, outputs, ");
		start = s.indexOf('(', end)+1;
		end = s.indexOf(',',start);
		String endState = s.substring(start, end);
		if(!states.contains(startState))
			states.add(startState);
		if(!states.contains(endState))
			states.add(endState);
		String[] endingValues = s.substring(0,s.indexOf(')')).split(",");
		String probability = endingValues[endingValues.length-1].trim();
		String duration = endingValues[endingValues.length-2].trim();
		boolean persistent = false;
		if(duration.contains("[")){
			String[] ints = duration.substring(1, duration.length()-1).split("-");
			String min = ints[0].trim();
			String max = ints[1].trim();
			duration = "new Range(" + min + ", " + max + ")";
		}else{
			if(duration.contains("-p")){
				duration = duration.substring(0, duration.indexOf('-'));
				persistent = true;
				memory.append("\n\t_internal_vars.addVariable(\"START_TIME\", -1);");
				memory.append("\n\t_internal_vars.addVariable(\"DURATION\", -1);");
			}
			duration = "Duration." + duration + ".getRange()";
		}
		String priority = endingValues[endingValues.length-3].trim();
		transition.append(endState + ", " + duration + ", "  + priority + ", "+ probability);
		if(persistent){
			transition.append(", true");
		}
		transition.append(") {");
		//end method call generation
		
		transition.append("\n\t\t@Override\n\t\tpublic boolean isEnabled() { ");
		start = s.indexOf('[')+1;
		end = s.indexOf(']', start);
		String[] inputs;
		if(s.substring(start,end).contains(","))
			inputs = s.substring(start,end).split(",");
		else
			inputs = new String[]{s.substring(start,end)};
			
		//process input prerequisites
		for(String input : inputs){
			String comparator = null;
			if(input.contains("!="))
				comparator = "!=";
			else if(input.contains("="))
				comparator = "=";
			else
				continue;
			String[] division = input.split(comparator);
			String[] value_channel = getValue_Channel(division);

			//adds to the enumerations if not already present
			if(value_channel[0].length() > 0 && name.equals(value_channel[0].substring(0, value_channel[0].indexOf('.')))){
				if(!enumerations.containsKey(value_channel[1])){
					enumerations.put(value_channel[1], "\npublic enum " + value_channel[1] + "{\n\t" + division[1] + ",");
				}else{
					if(!enumerations.get(value_channel[1]).contains(division[1]))
						enumerations.put(value_channel[1], enumerations.get(value_channel[1]) + "\n\t" + division[1] + ",");
				}
			}
			//adds a check for transition prerequisites and the return false if those are not met
			if(!value_channel[1].equals("EVENT")){
				//if not event
				value_channel[0] += value_channel[1] + "." + division[1];
				transition.append("\n\t\t\tif(");
				if(comparator.equals("="))
					transition.append("!");
				transition.append(value_channel[0] + ".equals(_inputs.get(Channels." + 
						value_channel[1] + ".name()).getValue())) {\n\t\t\t\treturn false;\n\t\t\t}");
			}else{
				//if event
				transition.append("\n\t\t\tif(_inputs.get(Channels." +
						division[1] + ".name()).getValue() == null || !(Boolean)_inputs.get(Channels." +
						division[1] + ".name()).getValue()) {\n\t\t\t\treturn false;\n\t\t\t}");
			}
		}
		
		
		//process internal variable prerequisites
		start = s.indexOf('[',end)+1;
		end = s.indexOf(']', start);
		String[] internals;
		if(s.substring(start,end).contains(","))
			internals = s.substring(start,end).split(",");
		else
			internals = new String[]{s.substring(start,end)};
			
		for(String internal : internals){
			generateInternalEvaluation(memory, transition, internal);
		}
		end = s.indexOf('(', end);
		//handle temp outputs
		start = s.indexOf('[', end)+1;
		end = s.indexOf(']', start);
		String[] temp_outputs;
		if(s.substring(start,end).contains(","))
			temp_outputs = s.substring(start, end).split(",");
		else
			temp_outputs = new String[]{s.substring(start,end)};
		
		for(String temp_output : temp_outputs){
			if(temp_output.contains("=")){
				String[] division = temp_output.split("=");
				String[] value_channel = getValue_Channel(division);
				if(name.equals(value_channel[0].substring(0, value_channel[0].indexOf('.')))){
					if(!enumerations.containsKey(value_channel[1])){
						enumerations.put(value_channel[1], "\npublic enum " + value_channel[1] + "{\n\t" + division[1] + ",");
					}else{
						if(!enumerations.get(value_channel[1]).contains("\t" + division[1] + ","))
							enumerations.put(value_channel[1], enumerations.get(value_channel[1]) + "\n\t" + division[1] + ",");
//						else if ("PS_START_TRANSMIT_AOI_PS".equals(division[1]))
//							System.out.println(enumerations.get(value_channel[1]).toString());
					}
				}
				value_channel[0] += value_channel[1] + "." + division[1];
				transition.append("\n\t\t\tsetTempOutput(Channels." + value_channel[1] + ".name(), " + value_channel[0] + ");");
			}
		}
		
		//handle temp internals
		start = s.indexOf('[', end)+1;
		end = s.indexOf(']', start);
		String[] temp_internals;
		if(s.substring(start, end).contains(", ")){
			temp_internals = s.substring(start, end).split(", ");
		}else{
			temp_internals = s.substring(start, end).split(",");
		}
		for(String temp_internal : temp_internals){
			generateTempInternalAssignment(memory, transition, temp_internal);
		}
		//finish the source code and return the needed data
		if(persistent){
			transition.append("\n\t\t\tif((Integer)_internal_vars.getVariable(\"DURATION\") <= 0 )"
					+ "\n\t\t\t\t_internal_vars.setVariable(\"DURATION\", Simulator.getSim().getDuration(this.getDurationRange()));");
		}
		transition.append("\n\t\t\treturn true;\n\t\t}\n\t}); // in comments");
		return new String[]{transition.toString(), endState};
	}
	
	/**
	 * Transition := (CurrentState,[Channel=InputData*],[Memory{=||>||<...}InputValue*],Priority,Duration,Probability)x(NextState,[Channel=OutputData*],[Memory=OutputValue*])
	 * @param s
	 * @return
	 */
	private boolean correctFormat(String s) {
		Pattern pattern = Pattern.compile("\\([[A-Z0-9]_]*,"
				+ "\\[([ADVE](=|!=)[A-Z_]*)?(,[ADVE](=|!=)[A-Z_]*)*\\],"
				+ "\\[([A-Z_]*(=|>|<|!=|<=|>=)[A-Z_0-9]*)?(,[A-Z_]*(=|>|<|!=|<=|>=)[A-Z_0-9]*)*\\],"
				+ "\\d*,"
				+ "([A-Z_]*(\\-p)?|\\[\\d*\\-\\d*\\]),"
				+ "\\d?\\.\\d*\\)"
				+ "[xX]"
				+ "\\([[A-Z0-9]_]*,"
				+ "\\[([ADVE]*=[A-Z_~]*)?(,[ADVE]*=[A-Z_~]*)*\\],"
				+ "\\[([A-Z_]*[=><(!=)(<=)(>=)][A-Z_(++)(--)(\\-1)]*)?(,[A-Z_]*[=><(!=)(<=)(>=)][A-Z_(++)(--)(\\-1)]*)*\\]\\)");
		Matcher matcher = pattern.matcher(s);
		boolean match = matcher.matches();
		if(s.length() > 0 && s.startsWith("(")){
			pattern = Pattern.compile("\\([[A-Z0-9]_]*,.*,.*,.*,.*,.*\\)[xX]\\(.*,.*,.*\\)");
			if(!pattern.matcher(s).find())	//state name
				System.out.println(1);
			pattern = Pattern.compile("\\(.*,\\[([ADVE](=|!=)[A-Z_]*)?(,[ADVE](=|!=)[A-Z_]*)*\\],.*,.*,.*,.*\\)[xX]\\(.*,.*,.*\\)");
			if(!pattern.matcher(s).find())	//inputs
				System.out.println(2);
			pattern = Pattern.compile("\\(.*,.*,\\[([A-Z_]*(=|>|<|!=|<=|>=)[A-Z_0-9]*)?(,[A-Z_]*(=|>|<|!=|<=|>=)[A-Z_0-9]*)*\\],.*,.*,.*\\)[xX]\\(.*,.*,.*\\)");
			if(!pattern.matcher(s).find())	//internals
				System.out.println(3);
			pattern = Pattern.compile("\\(.*,.*,.*,\\d*,.*,.*\\)[xX]\\(.*,.*,.*\\)");
			if(!pattern.matcher(s).find())	//priority	num
				System.out.println(4);
			pattern = Pattern.compile("\\(.*,.*,.*,.*,([A-Z_]*(\\-p)?|\\[\\d*\\-\\d*\\]),.*\\)[xX]\\(.*,.*,.*\\)");
			if(!pattern.matcher(s).find())	//duration	[num-num]
				System.out.println(5);
			pattern = Pattern.compile("\\(.*,.*,.*,.*,.*,\\d?\\.\\d*\\)[xX]\\(.*,.*,.*\\)");
			if(!pattern.matcher(s).find())	
				System.out.println(6);
			pattern = Pattern.compile("\\(.*,.*,.*,.*,.*,.*\\)[xX]\\([[A-Z0-9]_]*,.*,.*\\)");
			if(!pattern.matcher(s).find())
				System.out.println(7);
			pattern = Pattern.compile("\\(.*,.*,.*,.*,.*,.*\\)[xX]\\(.*,\\[([A-Z_]*=[A-Z_~]*)?(,[A-Z_]*=[A-Z_~]*)*\\],.*\\)");
			if(!pattern.matcher(s).find())
				System.out.println(8);
			pattern = Pattern.compile("\\(.*,.*,.*,.*,.*,.*\\)[xX]\\(.*,.*,\\[([A-Z_]*[=><(!=)(<=)(>=)][A-Z_(++)(--)(\\-1)]*)?(,[A-Z_]*[=><(!=)(<=)(>=)][A-Z_(++)(--)(\\-1)]*)*\\]\\)");
			if(!pattern.matcher(s).find())
				System.out.println(9);
			
		}
		return match;
	}
	
	/**
	 * @param memory
	 * @param transition
	 * @param temp_internal
	 */
	private void generateTempInternalAssignment(StringBuilder memory,
			StringBuilder transition, String temp_internal) {
		if(temp_internal.contains("=")){
			String[] division = temp_internal.split("=");
			boolean add_to_memory = false;
			if(!memory.toString().contains(division[0])){
				memory.append("\n\t_internal_vars.addVariable(\"" + division[0] + "\", ");
				add_to_memory = true;
			}
			String value = "";
			if(division[1].equalsIgnoreCase("true") || division[1].equalsIgnoreCase("false")){
				value = division[1].toLowerCase();
				if(add_to_memory)
					memory.append("false);");
			} else if(division[1].equals("++")){
				value = "(Integer)_internal_vars.getVariable(\"" + division[0] + "\") + 1";
				if(add_to_memory)
					memory.append("0);");
			} else if(division[1].equals("--")){
				value = "(Integer)_internal_vars.getVariable(\"" + division[0] + "\") - 1";
				if(add_to_memory)
					memory.append("0);");
			} else {
			
				try{
					value = String.valueOf(Integer.parseInt(division[1]));
					if(add_to_memory)
						memory.append("0);");
				}catch(NumberFormatException e){
					value = "\"" + division[1].toUpperCase() + "\"";
					if(add_to_memory)
						memory.append("null);");
				}
			}
			transition.append("\n\t\t\tsetTempInternalVar(\"" + division[0] + "\", " + value + ");");
		}
	}
	
	/**
	 * @param memory
	 * @param transition
	 * @param internal
	 */
	private void generateInternalEvaluation(StringBuilder memory,
			StringBuilder transition, String internal) {
		//check for any of the comparison operators and terminate the source generation if they aren't found
		String operator = null;
		if(internal.contains("<="))
			operator = "<=";
		else if(internal.contains(">="))
			operator = ">=";
		else if(internal.contains("!="))
			operator = "!=";
		else if(internal.contains("="))
			operator = "=";
		else if(internal.contains("<"))
			operator = "<";
		else if(internal.contains(">"))
			operator = ">";
		else
			return;
		
		String[] division = internal.split(operator);
		//add the source for the initializeInternalVariables method
		boolean add_to_memory = false;
		if(!memory.toString().contains(division[0])){
			memory.append("\n\t_internal_vars.addVariable(\"" + division[0] + "\", ");
//			System.out.printf("%s\n%s\n",division[0],division[1]);
			add_to_memory = true;
		}
		String value = "";
		//if boolean, integer, or object set the string for value and finish the source for the initializeInternalVariables if necessary
		if (division[1].equals("++") || division[1].equals("--")){
			value = "new Integer(" + division[0] + ")";
			if(add_to_memory)
				memory.append("0);");
		}else if(division[1].equalsIgnoreCase("true") || division[1].equalsIgnoreCase("false")){
			value = "new Boolean(" + division[1].toLowerCase() + ")";
			//add the source for the initializeInternalVariables method
			if(add_to_memory)
				memory.append("false);");
		} else {
			try{
				value = "new Integer(" + Integer.parseInt(division[1]) + ")";
				//add the source for the initializeInternalVariables method
				if(add_to_memory)
					memory.append("0);");
			}catch(NumberFormatException e){
				value = "\"" + division[1] + "\"";
				//add the source for the initializeInternalVariables method
				if(add_to_memory)
					memory.append("\"\");");
			}
		}
		
		//add the source code
		transition.append("\n\t\t\tif(");
		switch(operator){
		case "=":
			transition.append("!" + value + ".equals(");
			break;
		case "<":
			transition.append("_internal_vars.getVariable(\"" + division[0] + "\") instanceof Integer && " + value + " <= (Integer) ");
			break;
		case ">":
			transition.append("_internal_vars.getVariable(\"" + division[0] + "\") instanceof Integer && " + value + " >= (Integer) ");
			break;
		case "!=":
			transition.append(value + ".equals(");
			break;
		case "<=":
			transition.append("_internal_vars.getVariable(\"" + division[0] + "\") instanceof Integer && " + value + " < (Integer) ");
			break;
		case ">=":
			transition.append("_internal_vars.getVariable(\"" + division[0] + "\") instanceof Integer && " + value + " > (Integer) ");
			break;
		}
		if(transition.toString().endsWith("("))
			transition.append("_internal_vars.getVariable (\"" + division[0] + "\"))) {\n\t\t\t\treturn false;\n\t\t\t}");
		else
			transition.append("_internal_vars.getVariable (\"" + division[0] + "\")) {\n\t\t\t\treturn false;\n\t\t\t}");

	}

	/**
	 * @param division
	 * @return
	 */
	private String[] getValue_Channel(String[] division) {
		String[] value_channel = new String[2];
		value_channel[0] = "";
		String prefix = "";
		if(division[1].startsWith("MM")){
			value_channel[0] = "MissionManager.";
			prefix = "MM";
		} else if(division[1].startsWith("OP")){
			value_channel[0] = "Operator.";
			prefix = "OP";
		} else if(division[1].startsWith("VO")){
			value_channel[0] = "VideoOperator.";
			prefix = "VO";
		} else if(division[1].startsWith("VGUI")){
			value_channel[0] = "VideoOperatorGui.";
			prefix = "VGUI";
		} else if(division[1].startsWith("OGUI")){
			value_channel[0] = "OperatorGui.";
			prefix = "OGUI";
		} else if(division[1].startsWith("PS")){
			value_channel[0] = "ParentSearch.";
			prefix = "PS";
		} else if(division[1].startsWith("UAVHAG")){
			value_channel[0] = "UAVHeightAboveGround.";
			prefix = "UAVHAG";
		} else if(division[1].startsWith("UAVVF")){
			value_channel[0] = "UAVVideoFeed.";
			prefix = "UAVVF";
		} else if(division[1].startsWith("UAVBAT")){
			value_channel[0] = "UAVBattery.";
			prefix = "UAVBAT";
		} else if(division[1].startsWith("UAVFP")){
			value_channel[0] = "UAVFlightPlan.";
			prefix = "UAVFP";
		} else if(division[1].startsWith("UAVS")){
			value_channel[0] = "UAVSignal.";
			prefix = "UAVS";
		} else if(division[1].startsWith("UAV")){
			value_channel[0] = "UAV.";
			prefix = "UAV";
		}
//		} else if(division[1].startsWith("HAG")){
//			value_channel[0] = "HeightAboveGroundEvent.";
//		} else if(division[1].startsWith("")){
//			
//		} else if(division[1].startsWith("")){
//			
//		}else{
//			System.out.println("uncaught: " + division[1]);
//		}
		value_channel[1] = getChannel(division, prefix);
		return value_channel;
	}
	/**
	 * @param division
	 * @param prefix
	 * @return
	 */
	private String getChannel(String[] division, String prefix) {
		String suffix = extractSuffix(division);
		String channel = "";
		switch(division[0]){
		case "A": channel = "AUDIO_";break;
		case "V": channel = "VIDEO_";break;
		case "D": channel = "DATA_";break;
		case "E": return "EVENT";
		}
		channel += prefix + "_" + suffix + "_COMM";
		return channel;
	}
	/**
	 * @param division
	 * @return
	 */
	private String extractSuffix(String[] division) {
		String suffix = "";
		if(division[1].endsWith("MM")){
			suffix = "MM";
		} else if(division[1].endsWith("OP")){
			suffix = "OP";
		} else if(division[1].endsWith("VO")){
			suffix = "VO";
		} else if(division[1].endsWith("VGUI")){
			suffix = "VGUI";
		} else if(division[1].endsWith("OGUI")){
			suffix = "OGUI";
		} else if(division[1].endsWith("PS")){
			suffix = "PS";
		} else if(division[1].endsWith("UAVBAT")){
			suffix = "UAVBAT";
		} else if(division[1].endsWith("UAVFP")){
			suffix = "UAVFP";
		} else if(division[1].endsWith("UAVS")){
			suffix = "UAVS";
		} else if(division[1].endsWith("UAVVF")){
			suffix = "UAVVF";
		} else if(division[1].endsWith("UAVHAG")){
			suffix = "UAVHAG";
		} else if(division[1].endsWith("UAV")){
			suffix = "UAV";
		}
		return suffix;
	}
	
	/**
	 * builds WiSARTeam.java
	 * @param tuple This is a map of actor names, Strings, to the channels they use, represented as a list of String[3].
	 * @param channelsByActor is a hashmap of each actor and its corresponding channels.
	 * The key is the actor/event name.
	 * The first element of a channel array is the channel name.
	 * The second element of a channel array is the channel type (must be "VISUAL", "AUDIO", or "DATA").
	 * The third element of a channel array is the channel direction (aka "OUTPUT" or "INPUT").
	 */
	public static ArrayList<String> buildTeamClass(HashMap<String, ArrayList<String[]>> channelsByActor){
		
		ArrayList<String> channels = new ArrayList<String>();
		//initialize text for new team class
		String text = "";
		text += "package model.team;"
				+ "\n"
				+ "\nimport model.actors.gen.*;"
				+ "\nimport model.events.*;"
				+ "\nimport simulator.*;"
				+ "\n"
				+ "\npublic class TestWiSARTeam extends Team {"
				+ "\n"
				+ "\n\tpublic TestWiSARTeam() {"
				+ "\n"
				+ "\n\t\t_com_channels = new ComChannelList();";
		
		//add communication channels to data structure
	    for (Entry<String, ArrayList<String[]>> actor : channelsByActor.entrySet()) {
	        String actorName = actor.getKey();
	        
	        ArrayList<String[]> channelList = actor.getValue();
	        for(String[] channel : channelList){
		        String channelName = channel[0];
		        if(!channels.contains(channelName))
		        	channels.add(channelName);
		        String channelType = channel[1];
		        if(channel[2].equals("OUTPUT")){
			        if(Pattern.matches(".*[eE][vV][eE][nN][tT].*", channelName)){
			        	text += "\n\t\t_com_channels.add( new ComChannel<Boolean>(Channels." + channelName + ".name(), false, ComChannel.Type.AUDIO) );";
			        }else{
			        	text += "\n\t\t_com_channels.add( new ComChannel<" + actorName + "." + channelName + ">(Channels." + channelName + ".name(), ComChannel.Type." + channelType + ", \"" + getSource(channelName) + "\", \"" + getDestination(channelName) + "\") );";
			        }
		        }
	        }
	    }
				
		text += "\n"
				+ "\n\t\tComChannelList inputs = new ComChannelList();"
				+ "\n\t\tComChannelList outputs = new ComChannelList();"
				+ "\n";
		
		//add channel assignments
	    for (Entry<String, ArrayList<String[]>> actor : channelsByActor.entrySet()) {
	        String actorName = actor.getKey();
	        
	        text += "\n\t\tinputs.clear();";
	        text += "\n\t\toutputs.clear();";
	        
	        ArrayList<String[]> channelList = actor.getValue();
	        for(String[] channel : channelList){
		        String channelName = channel[0];
		        String channelDirection = channel[2];

		        if(channelDirection.equals("INPUT")){
		        	text += "\n\t\tinputs.add(_com_channels.get(Channels." + channelName + ".name()));";
		        }else{
		        	text += "\n\t\toutputs.add(_com_channels.get(Channels." + channelName + ".name()));";
		        }
	        }
	        
	        if(Pattern.matches(".*[eE][vV][eE][nN][tT].*", actorName)){
		        text += "\n\t\tthis.addEvent(new " + actorName + "(inputs, outputs), 1);";
	        }else{
	        	text += "\n\t\tthis.addActor(new " + actorName + "(inputs, outputs));";
	        }
	        text += "\n";
	    }
		
		//terminate class
		text += "\n\t}"
				+ "\n"
				+ "\n}";
		
		//print the class
		try {
			String path_name = "src/model/team/TestWiSARTeam.java";
			//PrintWriter writer = new PrintWriter("src/model/team/WiSARTeam.java", "UTF-8");
			PrintWriter writer = new PrintWriter(path_name, "UTF-8");
			writer.println(text);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return channels;
	}

	private static String getSource(String channelName) {
		String result = "";
		
		int parsingSource = 0;
		for(int i=0; i<channelName.length() && parsingSource<3; i++){
			char nextChar = channelName.charAt(i);
			if(nextChar == '_'){
				parsingSource++;
			}else if(parsingSource == 2){
				result += nextChar;
			}
		}
		
		return result;
	}

	private static String getDestination(String channelName) {
		String result = "";
		
		int parsingDestination = 0;
		for(int i=0; i<channelName.length() && parsingDestination<2; i++){
			char nextChar = channelName.charAt(i);
			if(nextChar == '_'){
				parsingDestination++;
			}else if(parsingDestination == 1){
				result += nextChar;
			}
		}
		
		return result;
	}
	
	/**
	 * builds Durations.java
	 * @param durations This is a list of arrays of strings three long that represent a duration.
	 * The first string of the array represents the name.
	 * The second string of the array represents the minimum duration.
	 * The third string of the array represents the maximum duration.  
	 */
	public void buildDurationClass(ArrayList<String[]> durations){
		//initialize text of the class
		String text = "";
		text = "package model.team;"
				+ "\n"
				+ "\nimport simulator.Range;"
				+ "\n"
				+ "\npublic enum Duration {";
		
		//add durations
		for(String[] duration : durations){
			String name = duration[0];
			String min = duration[1];
			String max = duration[2];
			text += "\n\t" + name + "(" + min + "," + max + "),";
		}
		
		//add duration methods
		text += "\n"
				+ "\n\tprivate Integer _minimum;"
				+ "\n\tprivate Integer _maximum;"
				+ "\n\tprivate Range _range;"
				+ "\n"
				+ "\n\tDuration(int minimum, int maximum) {"
				+ "\n\t\t_minimum = minimum;"
				+ "\n\t\t_maximum = maximum;"
				+ "\n\t\t_range = new Range(_minimum, _maximum);"
				+ "\n\t}"
				+ "\n"
				+ "\n\tDuration(Integer duration){"
				+ "\n\t\t_minimum = duration;"
				+ "\n\t\t_maximum = duration;"
				+ "\n\t\t_range = new Range(_minimum, _maximum);"
				+ "\n\t}"
				+ "\n"
				+ "\n\tpublic Duration update(Integer duration){"
				+ "\n\t\t_minimum = duration;"
				+ "\n\t\t_maximum = duration;"
				+ "\n\t\t_range = new Range(_minimum, _maximum);"
				+ "\n\t\treturn this;"
				+ "\n\t}"
				+ "\n"
				+ "\n\tpublic String toString() {"
				+ "\n\t\tString result = \"\";"
				+ "\n\t\tif (_minimum == _maximum) {"
				+ "\n\t\t\tresult = Integer.toString(_maximum);"
				+ "\n\t\t} else {"
				+ "\n\t\t\tresult = \"(\" + Integer.toString(_minimum) + \"-\" + Integer.toString(_maximum) + \")\";"
				+ "\n\t\t}"
				+ "\n\t\treturn result;"
				+ "\n\t}"
				+ "\n"
				+ "\n\tpublic Range getRange() {"
				+ "\n\t\treturn _range;"
				+ "\n\t}"
				+ "\n}";
		
		try {
			PrintWriter writer = new PrintWriter("Duration.java", "UTF-8");
			//PrintWriter writer = new PrintWriter("src/model/team/Duration.java", "UTF-8");
			writer.println(text);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * builds Channels.java
	 * @param channels This is a list of all communication channel names.
	 */
	public static void buildChannelsClass(ArrayList<String> channels){
		//initialize the text of the class
		String text = "";
		text = "package model.team;\n\npublic enum Channels {";
		
		//add the channels
		for(String channelName : channels){
			text += "\n\t" + channelName + ",";
		}
		
		//terminate the class
		text += "\n}";
		
		//print the text
		try {
			PrintWriter writer = new PrintWriter("Channels.java", "UTF-8");
			writer.println(text);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
}
