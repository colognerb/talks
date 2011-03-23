package rurug.jruby;

//
// Nach: http://java.sun.com/developer/technicalArticles/scripting/jruby/
//

import javax.script.*;

public class EvalScript {
	public static void main(String[] args) throws Exception {
		ScriptEngineManager factory = new ScriptEngineManager();
		ScriptEngine engine = factory.getEngineByName("jruby");
		if(engine == null)
			throw new RuntimeException("JRuby not found ");
		engine.eval("puts 'Hello World!\n'");
    }
}

