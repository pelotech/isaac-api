package uk.ac.cam.cl.dtg.isaac.app;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import com.google.inject.Guice;
import com.google.inject.Injector;

import uk.ac.cam.cl.dtg.isaac.configuration.IsaacGuiceConfigurationModule;
import uk.ac.cam.cl.dtg.segue.api.SegueApiFacade;
import uk.ac.cam.cl.dtg.segue.api.SegueGuiceConfigurationModule;

/**
 * This class registers the resteasy handlers. The name is important since it is
 * used as a String in HttpServletDispatcherV3
 * 
 * @author acr31
 * 
 */
public class IsaacApplicationRegister extends Application {
	private Set<Object> singletons;
	
	public IsaacApplicationRegister(){
		singletons = new HashSet<Object>();
	}

	@Override
	public Set<Object> getSingletons() {
		// Registers segue singleton endpoints as /isaac/segue/api
		Injector injector = Guice.createInjector(new IsaacGuiceConfigurationModule(), new SegueGuiceConfigurationModule());
		this.singletons.add((injector.getInstance(SegueApiFacade.class)));		
		return this.singletons;
	}
	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> result = new HashSet<Class<?>>();
		result.add(IsaacController.class);
		result.add(OverviewResource.class);
		return result;
	}	
}
