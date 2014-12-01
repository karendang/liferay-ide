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

package com.liferay.ide.service.core;

import com.liferay.ide.service.core.job.BuildServiceJob;
import com.liferay.ide.service.core.job.BuildWSDDJob;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 * 
 * @author Gregory Amerson
 * @author Simon Jiang
 */
public class ServiceCore extends Plugin
{

    // The shared instance
    private static ServiceCore plugin;

    // The plug-in ID
    public static final String PLUGIN_ID = "com.liferay.ide.service.core"; //$NON-NLS-1$

    public static BuildServiceJob createBuildServiceJob( IProject project )
    {
        BuildServiceJob job = new BuildServiceJob( project );

        return job;
    }

    public static BuildWSDDJob createBuildWSDDJob( IProject project )
    {
        BuildWSDDJob job = new BuildWSDDJob( project );

        return job;
    }

    public static IStatus createErrorStatus( Exception e )
    {
        return new Status( IStatus.ERROR, PLUGIN_ID, e.getMessage(), e );
    }

    public static IStatus createErrorStatus( String msg )
    {
        return new Status( IStatus.ERROR, PLUGIN_ID, msg );
    }

    public static IStatus createErrorStatus( String msg, Exception e )
    {
        return new Status( IStatus.ERROR, PLUGIN_ID, msg, e );
    }

    public static IStatus createWarningStatus( String msg )
    {
        return new Status( IStatus.WARNING, PLUGIN_ID, msg );
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static ServiceCore getDefault()
    {
        return plugin;
    }

    public static void logError( String msg, Exception e )
    {
        getDefault().getLog().log( createErrorStatus( msg, e ) );
    }

    public static void logError( Throwable t )
    {
        getDefault().getLog().log( new Status( IStatus.ERROR, PLUGIN_ID, t.getMessage(), t ) );
    }

    /**
     * The constructor
     */
    public ServiceCore()
    {
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    public void start( BundleContext context ) throws Exception
    {
        super.start( context );
        plugin = this;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    public void stop( BundleContext context ) throws Exception
    {
        plugin = null;
        super.stop( context );
    }

}
