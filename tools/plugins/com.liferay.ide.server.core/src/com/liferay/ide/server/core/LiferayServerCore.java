/*******************************************************************************
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 *******************************************************************************/

package com.liferay.ide.server.core;

import com.liferay.ide.core.LiferayCore;
import com.liferay.ide.core.util.CoreUtil;
import com.liferay.ide.core.util.StringPool;
import com.liferay.ide.sdk.core.ISDKListener;
import com.liferay.ide.sdk.core.SDKManager;
import com.liferay.ide.server.remote.IRemoteServer;
import com.liferay.ide.server.remote.IServerManagerConnection;
import com.liferay.ide.server.remote.ServerManagerConnection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IRuntimeLifecycleListener;
import org.eclipse.wst.server.core.IRuntimeType;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.IServerLifecycleListener;
import org.eclipse.wst.server.core.IServerType;
import org.eclipse.wst.server.core.ServerCore;
import org.eclipse.wst.server.core.internal.Base;
import org.eclipse.wst.server.core.internal.IMemento;
import org.eclipse.wst.server.core.internal.XMLMemento;
import org.eclipse.wst.server.core.model.RuntimeDelegate;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plugin life cycle
 *
 * @author Greg Amerson
 * @author Simon Jiang
 */
@SuppressWarnings( "restriction" )
public class LiferayServerCore extends Plugin
{

    private static Map<String, IServerManagerConnection> connections = null;

    // The shared instance
    private static LiferayServerCore plugin;

    // The plugin ID
    public static final String PLUGIN_ID = "com.liferay.ide.server.core"; //$NON-NLS-1$

    private static IPluginPublisher[] pluginPublishers = null;

    private static IRuntimeDelegateValidator[] runtimeDelegateValidators;

    private static ILiferayRuntimeStub[] runtimeStubs;

    public static IStatus createErrorStatus( Exception e )
    {
        return createErrorStatus( PLUGIN_ID, e );
    }

    public static IStatus createErrorStatus( String msg )
    {
        return createErrorStatus( PLUGIN_ID, msg );
    }

    public static IStatus createErrorStatus( String pluginId, String msg )
    {
        return new Status( IStatus.ERROR, pluginId, msg );
    }

    public static IStatus createErrorStatus( String pluginId, String msg, Throwable e )
    {
        return new Status( IStatus.ERROR, pluginId, msg, e );
    }

    public static IStatus createErrorStatus( String pluginId, Throwable t )
    {
        return new Status( IStatus.ERROR, pluginId, t.getMessage(), t );
    }

    public static IStatus createInfoStatus( String msg )
    {
        return new Status( IStatus.INFO, PLUGIN_ID, msg );
    }

    public static IStatus createWarningStatus( String message )
    {
        return new Status( IStatus.WARNING, PLUGIN_ID, message );
    }

    public static IStatus createWarningStatus( String message, String id )
    {
        return new Status( IStatus.WARNING, id, message );
    }

    public static IStatus createWarningStatus( String message, String id, Exception e )
    {
        return new Status( IStatus.WARNING, id, message, e );
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static LiferayServerCore getDefault()
    {
        return plugin;
    }

    public static URL getPluginEntry( String path )
    {
        return getDefault().getBundle().getEntry( path );
    }

    public static IPluginPublisher getPluginPublisher( String facetId, String runtimeTypeId )
    {
        if( CoreUtil.isNullOrEmpty( facetId ) || CoreUtil.isNullOrEmpty( runtimeTypeId ) )
        {
            return null;
        }

        IPluginPublisher retval = null;
        IPluginPublisher[] publishers = getPluginPublishers();

        if( publishers != null && publishers.length > 0 )
        {
            for( IPluginPublisher publisher : publishers )
            {
                if( publisher != null && facetId.equals( publisher.getFacetId() ) &&
                    runtimeTypeId.equals( publisher.getRuntimeTypeId() ) )
                {
                    retval = publisher;
                    break;
                }
            }
        }

        return retval;
    }

    public static IPluginPublisher[] getPluginPublishers()
    {
        if( pluginPublishers == null )
        {
            IConfigurationElement[] elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor( IPluginPublisher.ID );

            try
            {
                List<IPluginPublisher> deployers = new ArrayList<IPluginPublisher>();

                for( IConfigurationElement element : elements )
                {
                    final Object o = element.createExecutableExtension( "class" ); //$NON-NLS-1$

                    if( o instanceof AbstractPluginPublisher )
                    {
                        AbstractPluginPublisher pluginDeployer = (AbstractPluginPublisher) o;
                        pluginDeployer.setFacetId( element.getAttribute( "facetId" ) ); //$NON-NLS-1$
                        pluginDeployer.setRuntimeTypeId( element.getAttribute( "runtimeTypeId" ) ); //$NON-NLS-1$
                        deployers.add( pluginDeployer );
                    }
                }

                pluginPublishers = deployers.toArray( new IPluginPublisher[0] );
            }
            catch( Exception e )
            {
                logError( "Unable to get plugin deployer extensions", e ); //$NON-NLS-1$
            }
        }

        return pluginPublishers;
    }

    public static URL getPortalSupportLibURL()
    {
        try
        {
            return FileLocator.toFileURL( LiferayServerCore.getPluginEntry( "/portal-support/portal-support.jar" ) ); //$NON-NLS-1$
        }
        catch( IOException e )
        {
        }

        return null;
    }

    public static IServerManagerConnection getRemoteConnection( final IRemoteServer server )
    {
        if( connections == null )
        {
            connections = new HashMap<String, IServerManagerConnection>();
        }

        IServerManagerConnection service = connections.get( server.getId() );

        if( service == null )
        {
            service = new ServerManagerConnection();

            updateConnectionSettings( server, service );

            connections.put( server.getId(), service );
        }
        else
        {
            updateConnectionSettings( server, service );
        }

        return service;
    }

    public static IRuntimeDelegateValidator[] getRuntimeDelegateValidators()
    {
        if( runtimeDelegateValidators == null )
        {
            IConfigurationElement[] elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor( IRuntimeDelegateValidator.ID );

            try
            {
                List<IRuntimeDelegateValidator> validators = new ArrayList<IRuntimeDelegateValidator>();

                for( IConfigurationElement element : elements )
                {
                    final Object o = element.createExecutableExtension( "class" ); //$NON-NLS-1$
                    final String runtimeTypeId = element.getAttribute( "runtimeTypeId" ); //$NON-NLS-1$

                    if( o instanceof AbstractRuntimeDelegateValidator )
                    {
                        AbstractRuntimeDelegateValidator validator = (AbstractRuntimeDelegateValidator) o;
                        validator.setRuntimeTypeId( runtimeTypeId );
                        validators.add( validator );
                    }
                }

                runtimeDelegateValidators = validators.toArray( new IRuntimeDelegateValidator[0] );
            }
            catch( Exception e )
            {
                logError( "Unable to get IRuntimeDelegateValidator extensions", e ); //$NON-NLS-1$
            }
        }

        return runtimeDelegateValidators;
    }

    public static ILiferayRuntimeStub getRuntimeStub( String stubTypeId )
    {
        ILiferayRuntimeStub retval = null;

        ILiferayRuntimeStub[] stubs = getRuntimeStubs();

        if( !CoreUtil.isNullOrEmpty( stubs ) )
        {
            for( ILiferayRuntimeStub stub : stubs )
            {
                if( stub.getRuntimeStubTypeId().equals( stubTypeId ) )
                {
                    retval = stub;
                    break;
                }
            }
        }

        return retval;
    }

    public static ILiferayRuntimeStub[] getRuntimeStubs()
    {
        if( runtimeStubs == null )
        {
            IConfigurationElement[] elements =
                Platform.getExtensionRegistry().getConfigurationElementsFor( ILiferayRuntimeStub.EXTENSION_ID );

            if( !CoreUtil.isNullOrEmpty( elements ) )
            {
                List<ILiferayRuntimeStub> stubs = new ArrayList<ILiferayRuntimeStub>();

                for( IConfigurationElement element : elements )
                {
                    String runtimeTypeId = element.getAttribute( ILiferayRuntimeStub.RUNTIME_TYPE_ID );
                    String name = element.getAttribute( ILiferayRuntimeStub.NAME );
                    boolean isDefault = Boolean.parseBoolean( element.getAttribute( ILiferayRuntimeStub.DEFAULT ) );

                    try
                    {
                        LiferayRuntimeStub stub = new LiferayRuntimeStub();
                        stub.setRuntimeTypeId( runtimeTypeId );
                        stub.setName( name );
                        stub.setDefault( isDefault );

                        stubs.add( stub );
                    }
                    catch( Exception e )
                    {
                        logError( "Could not create liferay runtime stub.", e ); //$NON-NLS-1$
                    }
                }

                runtimeStubs = stubs.toArray( new ILiferayRuntimeStub[0] );
            }
        }

        return runtimeStubs;
    }

    public static IPath getTempLocation( String prefix, String fileName )
    {
        return getDefault().getStateLocation().append( "tmp" ).append( //$NON-NLS-1$
            prefix + "/" + System.currentTimeMillis() + ( CoreUtil.isNullOrEmpty( fileName ) ? StringPool.EMPTY : "/" + fileName ) ); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public static void logError( Exception e )
    {
        getDefault().getLog().log( new Status( IStatus.ERROR, PLUGIN_ID, e.getMessage(), e ) );
    }

    public static void logError( IStatus status )
    {
        getDefault().getLog().log( status );
    }

    public static void logError( String msg )
    {
        logError( createErrorStatus( msg ) );
    }

    public static void logError( String msg, Throwable e )
    {
        getDefault().getLog().log( new Status( IStatus.ERROR, PLUGIN_ID, msg, e ) );
    }

    public static void logError( Throwable t )
    {
        getDefault().getLog().log( new Status( IStatus.ERROR, PLUGIN_ID, t.getMessage(), t ) );
    }

    public static void updateConnectionSettings( IRemoteServer server )
    {
        updateConnectionSettings( server, getRemoteConnection( server ) );
    }

    public static void updateConnectionSettings( IRemoteServer server, IServerManagerConnection remoteConnection )
    {
        remoteConnection.setHost( server.getHost() );
        remoteConnection.setHttpPort( server.getHTTPPort() );
        remoteConnection.setManagerContextPath( server.getServerManagerContextPath() );
        remoteConnection.setUsername( server.getUsername() );
        remoteConnection.setPassword( server.getPassword() );
    }

    public static IStatus validateRuntimeDelegate( RuntimeDelegate runtimeDelegate )
    {
        if( runtimeDelegate.getRuntime().isStub() )
        {
            return Status.OK_STATUS;
        }

        String runtimeTypeId = runtimeDelegate.getRuntime().getRuntimeType().getId();

        IRuntimeDelegateValidator[] validators = getRuntimeDelegateValidators();

        if( !CoreUtil.isNullOrEmpty( validators ) )
        {
            for( IRuntimeDelegateValidator validator : validators )
            {
                if( runtimeTypeId.equals( validator.getRuntimeTypeId() ) )
                {
                    IStatus status = validator.validateRuntimeDelegate( runtimeDelegate );

                    if( !status.isOK() )
                    {
                        return status;
                    }
                }
            }
        }

        return Status.OK_STATUS;
    }

    private IRuntimeLifecycleListener runtimeLifecycleListener;

    private ISDKListener sdkListener;

    private IServerLifecycleListener serverLifecycleListener;

    /**
     * The constructor
     */
    public LiferayServerCore()
    {
    }

    private boolean addRuntimeToMemento( IRuntime runtime, IMemento memento )
    {
        if( runtime instanceof Base )
        {
            final Base base = (Base) runtime;

            try
            {
                final Method save = Base.class.getDeclaredMethod( "save", IMemento.class );

                if( save != null )
                {
                    save.setAccessible( true );
                    save.invoke( base, memento );
                }

                return true;
            }
            catch( Exception e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return false;
    }

    private boolean addServerToMemento( IServer server, IMemento memento )
    {
        if( server instanceof Base )
        {
            final Base base = (Base) server;

            try
            {
                final Method save = Base.class.getDeclaredMethod( "save", IMemento.class );

                if( save != null )
                {
                    save.setAccessible( true );
                    save.invoke( base, memento );
                }

                return true;
            }
            catch( Exception e )
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        return false;
    }

    private synchronized void saveGlobalRuntimeSettings( IRuntime runtime )
    {
        final IRuntimeType runtimeType = runtime.getRuntimeType();

        if( runtimeType != null && runtimeType.getId().startsWith( "com.liferay" ) )
        {
            try
            {
                LiferayCore.GLOBAL_SETTINGS_PATH.toFile().mkdirs();
                final File runtimesGlobalFile =
                    LiferayCore.GLOBAL_SETTINGS_PATH.append( "runtimes.xml" ).toFile();

                final Set<IMemento> existing = new HashSet<IMemento>();

                if( runtimesGlobalFile.exists() )
                {
                    try
                    {
                        final IMemento existingMemento =
                            XMLMemento.loadMemento( new FileInputStream( runtimesGlobalFile ) );

                        if( existingMemento != null )
                        {
                            final IMemento[] children = existingMemento.getChildren( "runtime" );

                            if( ! CoreUtil.isNullOrEmpty( children ) )
                            {
                                for( IMemento child : children )
                                {
                                    final IPath loc = Path.fromPortableString( child.getString( "location" ) );

                                    if( loc != null && loc.toFile().exists() )
                                    {
                                        boolean duplicate =
                                            ServerCore.findRuntime( child.getString( "id" ) ) != null;

                                        if( ! duplicate )
                                        {
                                            existing.add( child );
                                        }
                                    }
                                }
                            }
                        }
                    }
                    catch( Exception e )
                    {
                    }
                }

                final Map<String, IMemento> mementos = new HashMap<String, IMemento>();

                final XMLMemento runtimeMementos = XMLMemento.createWriteRoot( "runtimes" );

                for( IMemento exist : existing )
                {
                    final IMemento copy = runtimeMementos.createChild( "runtime" );
                    copyMemento( exist, copy );

                    mementos.put( copy.getString( "id" ), copy );
                }

                for( IRuntime r : ServerCore.getRuntimes() )
                {
                    if( mementos.get( r.getId() ) == null && r.getRuntimeType() != null )
                    {
                        final IMemento rMemento = runtimeMementos.createChild( "runtime" );

                        if( addRuntimeToMemento( r, rMemento ) )
                        {
                            mementos.put( r.getId(), rMemento );
                        }
                    }
                }

                final FileOutputStream fos = new FileOutputStream( runtimesGlobalFile );

                runtimeMementos.save( fos );
            }
            catch( Exception e )
            {
                LiferayServerCore.logError( "Unable to save global runtime settings", e );
            }
        }
    }

    private void copyMemento( IMemento from, IMemento to )
    {
        for( String name : from.getNames() )
        {
            to.putString( name, from.getString( name ) );
        }
    }

    private synchronized void saveGlobalServerSettings( IServer server )
    {
        final IServerType serverType = server.getServerType();

        if( serverType != null && serverType.getId().startsWith( "com.liferay" ) )
        {
            try
            {
                LiferayCore.GLOBAL_SETTINGS_PATH.toFile().mkdirs();
                final File globalServersFile = LiferayCore.GLOBAL_SETTINGS_PATH.append( "servers.xml" ).toFile();
                final Set<IMemento> existing = new HashSet<IMemento>();

                if( globalServersFile.exists() )
                {
                    try
                    {
                        final IMemento existingMemento =
                            XMLMemento.loadMemento( new FileInputStream( globalServersFile ) );

                        if( existingMemento != null )
                        {
                            final IMemento[] children = existingMemento.getChildren( "server" );

                            if( ! CoreUtil.isNullOrEmpty( children ) )
                            {
                                for( IMemento child : children )
                                {
                                    final boolean duplicate =
                                        ServerCore.findServer( child.getString( "id" ) ) != null;

                                    if( ! duplicate )
                                    {
                                        existing.add( child );
                                    }
                                }
                            }
                        }
                    }
                    catch( Exception e )
                    {
                    }
                }

                final Map<String, IMemento> mementos = new HashMap<String, IMemento>();

                final XMLMemento serverMementos = XMLMemento.createWriteRoot( "servers" );

                for( IMemento exist : existing )
                {
                    final IMemento copy = serverMementos.createChild( "server" );
                    copyMemento( exist, copy );
                    mementos.put( copy.getString( "id" ), copy );
                }

                for( IServer s : ServerCore.getServers() )
                {
                    if( mementos.get( s.getId() ) == null && s.getServerType() != null )
                    {
                        final IMemento sMemento = serverMementos.createChild( "server" );

                        if( addServerToMemento( s, sMemento ) )
                        {
                            mementos.put( s.getId(), sMemento );
                        }
                    }
                }

                if( mementos.size() > 0 )
                {
                    final FileOutputStream fos = new FileOutputStream( globalServersFile );

                    serverMementos.save( fos );
                }
            }
            catch( Exception e )
            {
                LiferayServerCore.logError( "Unable to save global server settings", e );
            }
        }
    }


    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext )
     */
    public void start( BundleContext context ) throws Exception
    {
        super.start( context );

        plugin = this;

        this.runtimeLifecycleListener = new IRuntimeLifecycleListener()
        {
            public void runtimeAdded( IRuntime runtime )
            {
                saveGlobalRuntimeSettings( runtime );
            }

            public void runtimeChanged( IRuntime runtime )
            {
                saveGlobalRuntimeSettings( runtime );
            }

            public void runtimeRemoved( IRuntime runtime )
            {
                saveGlobalRuntimeSettings( runtime );
            }
        };

        this.serverLifecycleListener = new IServerLifecycleListener()
        {
            public void serverAdded( IServer server )
            {
                saveGlobalServerSettings( server );
            }

            public void serverChanged( IServer server )
            {
                saveGlobalServerSettings( server );
            }

            public void serverRemoved( IServer server )
            {
                saveGlobalServerSettings( server );

                if( connections.get( server.getId() ) != null )
                {
                    connections.put( server.getId(), null );
                }
            }
        };

        ServerCore.addRuntimeLifecycleListener( this.runtimeLifecycleListener );
        ServerCore.addServerLifecycleListener( this.serverLifecycleListener );
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext )
     */
    public void stop( BundleContext context ) throws Exception
    {
        plugin = null;

        super.stop( context );

        SDKManager.getInstance().removeSDKListener( this.sdkListener );
        ServerCore.removeRuntimeLifecycleListener( runtimeLifecycleListener );
        ServerCore.removeServerLifecycleListener( serverLifecycleListener );
    }
}
