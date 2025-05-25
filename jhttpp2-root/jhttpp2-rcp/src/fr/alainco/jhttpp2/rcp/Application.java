package fr.alainco.jhttpp2.rcp;

import java.util.Map;

import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;

public class Application implements IApplication {

	@Override
	public Object start(final IApplicationContext context) throws Exception {
		System.out.println("Start " + this.getClass().getName());
		final Map<?, ?> args = context.getArguments();
		String[] appArgs = (String[]) args.get("application.args");
		fr.alainco.jhttpp2.console.Jhttpp2Launcher.runWithArgs(appArgs);
		return IApplication.EXIT_OK;
	}

	@Override
	public void stop() {
		// nothing to do
	}

}
