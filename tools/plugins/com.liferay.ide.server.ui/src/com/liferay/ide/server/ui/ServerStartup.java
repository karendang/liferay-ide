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

package com.liferay.ide.server.ui;

import com.liferay.ide.core.LiferayCore;
import com.liferay.ide.core.util.CoreUtil;
import com.liferay.ide.sdk.core.SDK;
import com.liferay.ide.sdk.core.SDKManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Date;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.mylyn.commons.notifications.core.AbstractNotification;
import org.eclipse.mylyn.commons.notifications.ui.AbstractUiNotification;
import org.eclipse.mylyn.commons.notifications.ui.NotificationsUi;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.wst.server.core.IRuntime;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.internal.Base;
import org.eclipse.wst.server.core.internal.IMemento;
import org.eclipse.wst.server.core.internal.IStartup;
import org.eclipse.wst.server.core.internal.ResourceManager;
import org.eclipse.wst.server.core.internal.Runtime;
import org.eclipse.wst.server.core.internal.Server;
import org.eclipse.wst.server.core.internal.XMLMemento;
import org.osgi.service.prefs.BackingStoreException;

/**
 * @author Gregory Amerson
 */
@SuppressWarnings( "restriction" )
public class ServerStartup implements IStartup
{
    private static final String GLOBAL_SETTINGS_CHECKED = "global-settings-checked";

    public void startup()
    {
        if( shouldCheckForGlobalSettings() && hasGlobalSettings() )
        {
            NotificationsUi.getService().notify(
                Collections.singletonList( createImportGlobalSettingsNotification() ) );
        }
    }

    private boolean hasGlobalSettings()
    {
        final File globalSettingsDir = LiferayCore.GLOBAL_SETTINGS_PATH.toFile();

        if( globalSettingsDir.exists() )
        {
            final File[] settings = globalSettingsDir.listFiles( new FilenameFilter()
            {
                public boolean accept( File dir, String name )
                {
                    return name.toLowerCase().endsWith( ".xml" );
                }
            });

            return settings != null && settings.length > 0;
        }

        return false;
    }

    private AbstractNotification createImportGlobalSettingsNotification()
    {
        final Date date = new Date();
        return new  AbstractUiNotification( "com.liferay.ide.server.ui.importglobalsettings" )
        {
            @SuppressWarnings( "rawtypes" )
            public Object getAdapter( Class adapter )
            {
                return null;
            }

            @Override
            public Image getNotificationImage()
            {
                return LiferayServerUIPlugin.getDefault().getImageRegistry().get(
                    LiferayServerUIPlugin.IMG_NOTIFICATION );
            }

            @Override
            public Image getNotificationKindImage()
            {
                return null;
            }

            @Override
            public void open()
            {
                boolean importSettings = MessageDialog.openQuestion(
                    PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
                    "Previous Liferay IDE Settings Detected",
                    "Settings from a previous Liferay IDE workspace have been detected such as: Plugins SDKS, Liferay runtimes, or Liferay servers. Do you want to import these settings now?" );

                if( importSettings )
                {
                    importGlobalSettings();
                }

                try
                {
                    IEclipsePreferences prefs =
                        InstanceScope.INSTANCE.getNode( LiferayServerUIPlugin.PLUGIN_ID );
                    prefs.putBoolean( GLOBAL_SETTINGS_CHECKED, true );
                    prefs.flush();
                }
                catch( BackingStoreException e )
                {
                    LiferayServerUIPlugin.logError( "Unable to persist global-setting-checked pref", e );
                }
            }

            @Override
            public Date getDate()
            {
                return date;
            }

            @Override
            public String getDescription()
            {
                return "Select the above link for more information.";
            }

            @Override
            public String getLabel()
            {
                return "Previous Liferay IDE settings available for import";
            }
        };
    }

    private void importGlobalSettings()
    {
        final File settingsDir = LiferayCore.GLOBAL_SETTINGS_PATH.toFile();

        if( settingsDir.exists() )
        {
            final File sdks = new File( settingsDir, "sdks.xml" );

            if( sdks.exists() )
            {
                importGlobalSDKs( sdks );
            }

            final File runtimes = new File( settingsDir, "runtimes.xml" );

            if( runtimes.exists() )
            {
                importGlobalRuntimes( runtimes );
            }

            final File servers = new File( settingsDir, "servers.xml" );

            if( servers.exists() )
            {
                importGlobalServers( servers );
            }
        }
    }

    private void importGlobalServers( File serversFile )
    {
        try
        {
            final IMemento serversMemento = XMLMemento.loadMemento( new FileInputStream ( serversFile ) );

            if( serversMemento != null )
            {
                final ResourceManager resourceManager = ResourceManager.getInstance();

                final IMemento[] mementos = serversMemento.getChildren( "server" );

                if( ! CoreUtil.isNullOrEmpty( mementos ) )
                {
                    for( IMemento memento : mementos )
                    {
                        final Server server = new Server( null );

                        try
                        {
                            final Method loadFromMemento =
                                Base.class.getDeclaredMethod(
                                    "loadFromMemento", IMemento.class, IProgressMonitor.class );

                            if( loadFromMemento != null )
                            {
                                loadFromMemento.setAccessible( true );
                                loadFromMemento.invoke( server, memento, null );

                                final Method addServer =
                                    ResourceManager.class.getDeclaredMethod( "addServer", IServer.class );

                                if( addServer != null )
                                {
                                    addServer.setAccessible( true );
                                    addServer.invoke( resourceManager, server );
                                }
                            }
                        }
                        catch( Exception e )
                        {
                            // TODO auto-generated catch
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        catch( FileNotFoundException e )
        {
        }
    }

    private void importGlobalRuntimes( File runtimesFile )
    {
        try
        {
            final IMemento runtimesMemento = XMLMemento.loadMemento( new FileInputStream ( runtimesFile ) );

            if( runtimesMemento != null )
            {
                final ResourceManager resourceManager = ResourceManager.getInstance();

                final IMemento[] mementos = runtimesMemento.getChildren( "runtime" );

                if( ! CoreUtil.isNullOrEmpty( mementos ) )
                {
                    for( IMemento memento : mementos )
                    {
                        final Runtime runtime = new Runtime( null );

                        try
                        {
                            final Method loadFromMemento =
                                Base.class.getDeclaredMethod(
                                    "loadFromMemento", IMemento.class, IProgressMonitor.class );

                            if( loadFromMemento != null )
                            {
                                loadFromMemento.setAccessible( true );
                                loadFromMemento.invoke( runtime, memento, null );

                                final Method addRuntime =
                                    ResourceManager.class.getDeclaredMethod( "addRuntime", IRuntime.class );

                                if( addRuntime != null )
                                {
                                    addRuntime.setAccessible( true );
                                    addRuntime.invoke( resourceManager, runtime );
                                }
                            }
                        }
                        catch( Exception e )
                        {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        catch( FileNotFoundException e )
        {
        }
    }

    private void importGlobalSDKs( File sdksFile )
    {
        try
        {
            final IMemento sdksMemento = XMLMemento.loadMemento( new FileInputStream ( sdksFile ) );

            if( sdksMemento != null )
            {
                final IMemento[] sdks = sdksMemento.getChildren( "sdk" );

                if( ! CoreUtil.isNullOrEmpty( sdks ) )
                {
                    for( IMemento sdkMemento : sdks )
                    {
                        final SDK newSDK = createSDKfromMemento( sdkMemento );

                        if( newSDK != null )
                        {
                            SDKManager.getInstance().addSDK( newSDK );
                        }
                    }
                }
            }
        }
        catch( FileNotFoundException e )
        {
        }
    }

    private SDK createSDKfromMemento( IMemento memento )
    {
        SDK sdk = new SDK();

        sdk.setName( memento.getString( "name" ) );
        sdk.setLocation( Path.fromPortableString( memento.getString( "location" ) ) );

        return sdk;
    }

    private boolean shouldCheckForGlobalSettings()
    {
        IScopeContext[] scopes = new IScopeContext[] { InstanceScope.INSTANCE };

        return !( Platform.getPreferencesService().getBoolean(
            LiferayServerUIPlugin.PLUGIN_ID, GLOBAL_SETTINGS_CHECKED, false, scopes ) );
    }

}
