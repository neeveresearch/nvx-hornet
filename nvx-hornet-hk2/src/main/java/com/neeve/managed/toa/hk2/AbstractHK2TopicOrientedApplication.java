package com.neeve.managed.toa.hk2;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.Binder;

import com.neeve.aep.AepEngineDescriptor;
import com.neeve.managed.ManagedObjectLocator;
import com.neeve.managed.annotations.Managed;
import com.neeve.managed.hk2.HK2ManagedObjectLocator;
import com.neeve.toa.TopicOrientedApplication;

/**
 * The {@link AbstractHK2TopicOrientedApplication} is the base class for TOA Applications that want to leverage the 
 * <a href="https://hk2.java.net/">HK2 Dependency Injection Framework</a>. HK2 provides a light-weight but powerful dependency injection
 * mechanism. The {@link AbstractHK2TopicOrientedApplication} integrates HK2 with Talon Application Server and the Hornet Application 
 * Framework so that HK2 can be used to discover {@link Managed Talon Managed Objects} and Hornet Applications gain access to a 
 * {@link #getApplicationServiceLocator() Application ServiceLocator}. Hornet Applications that want to leverage HK2 must extend this
 * class and not {@link TopicOrientedApplication}.
 * 
 * @see com.neeve.managed.toa.hk2
 **/
//  TODO: In the future when a TopicOrientedApplication shuts down via onAppFinalized() we must destroy the HK2 ServiceLocator we created
public abstract class AbstractHK2TopicOrientedApplication extends TopicOrientedApplication {

	// TODO: in the future we will replace this with a direct ref to the ServiceLocator and we will want to make HK2ManagedObjectLocator
	// a package-private class so that it is no longer part of the public API
	private HK2ManagedObjectLocator managedObjectLocator;
	
	/**
	 * The {@link AbstractHK2TopicOrientedApplication} extends the life-cycle of the {@link TopicOrientedApplication} to allow for the
	 * creation of an {@link ServiceLocator HK2 ServiceLocator}. The construction of the 
	 * {@link #getApplicationServiceLocator() Application ServiceLocator} happens early, during the {@link AepEngineDescriptor} customization 
	 * phase so that components and/or services can be given the chance to participate in the initialization of the Application and
	 * the AEP Engine. Specifically, when this method is invoked two things happen (after the standard TopicOrientedApplication life-cycle):
	 * 
	 * <h2>4.1 Application ServiceLocator Creation</h2>
	 * 
	 * When the {@link AepEngineDescriptor} is injected the {@link AbstractHK2TopicOrientedApplication} will construct the 
	 * {@link #getApplicationServiceLocator() Application ServiceLocator}. After this point the Application may use the 
	 * Application Service Locator to retrieve Services or other information (such as Configuration) from the ServiceLocator.
	 * 
	 * <h2>4.2 Application Service Injection</h2>
	 * 
	 * Once Application ServiceLocator has been constructed the Application object may have dependencies {@link Inject injected} into this
	 * instance. These injected dependencies will be satisfied based upon the standard HK2 injection strategy. Note that only after this
	 * point will injected dependencies be available. 
	 */
	@Override
	protected void onEngineDescriptorInjected(AepEngineDescriptor engineDescriptor) throws Exception {
		super.onEngineDescriptorInjected(engineDescriptor);
		this.managedObjectLocator = createManagedObjectLocator();
		managedObjectLocator.getAppplicationServiceLocator().inject(this);
	}
	
	private HK2ManagedObjectLocator createManagedObjectLocator() {
		List<Binder> applicationModules = new ArrayList<Binder>(getApplicationModules());
		return new HK2ManagedObjectLocator(this, getName(), applicationModules);
	}
	
	@Override
	protected ManagedObjectLocator getManagedObjectLocator() {
		return managedObjectLocator;
	}
	
	/**
	 * Return the Application ServiceLocator. Every TOA HK2 Application is associated with a single {@link ServiceLocator HK2 ServiceLocator}.
	 * The Application ServiceLocator has a {@link ServiceLocator#getName() name} that is the same as the 
	 * {@link #getName() Application Name}. All the the {@link #getApplicationModules() Application Modules} will be bound into the 
	 * Application ServiceLocator. The Application ServiceLocator can be used (via the standard HK2 API) to retrieve any services or
	 * components that are configured in any of the Application Modules.
	 * 
	 * @return the Application ServiceLocator
	 * @throws IllegalStateException
	 * 		   if the Application ServiceLocator is not available. This may happen if {@link #onEngineDescriptorInjected(AepEngineDescriptor)}
	 * 		   has not been invoked yet or if the Application has been shutdown
	 */
	protected @Nonnull ServiceLocator getApplicationServiceLocator() {
		if (managedObjectLocator != null)
			return managedObjectLocator.getAppplicationServiceLocator();
		else
			throw new IllegalStateException("Application ServiceLocator unavailable.");
	}
	
	/**
	 * Get the Application Modules. The Application Modules is a list of HK2 Binders that will be bound to the 
	 * {@link #getApplicationServiceLocator() Application ServiceLocator}. This list may be empty but it must not be <code>null</code>.
	 * 
	 * @return the Application Modules
	 */
	protected abstract @Nonnull List<Binder> getApplicationModules();
	
	/**
	 * Get the Application Name. The <strong>Application Name</strong> is a short, human-readable string that uniquely identifies an
	 * Application in the Talon Server. Note that the name must be unique; two Applications running in the same Talon Server must not
	 * have the same Application Name. The Application Name will be used to construct a unique {@link #getApplicationServiceLocator() ServiceLocator}
	 * for the Application. The Application Name will be made available to components and services and can also be used to support qualified
	 * injection. The Application Name must not be <code>null</code> and must not be an empty string or a string that contains only whitespace.
	 * 
	 * @return the Application Name
	 */
	protected abstract @Nonnull String getName();
}
