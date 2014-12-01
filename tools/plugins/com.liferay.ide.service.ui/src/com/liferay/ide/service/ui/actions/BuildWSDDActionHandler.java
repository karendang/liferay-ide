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

package com.liferay.ide.service.ui.actions;

import com.liferay.ide.service.core.ServiceCore;
import com.liferay.ide.service.core.job.BuildWSDDJob;
import com.liferay.ide.service.ui.ServiceUIUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.sapphire.ui.Presentation;
import org.eclipse.sapphire.ui.SapphireActionHandler;

/**
 * @author Gregory Amerson
 */
public class BuildWSDDActionHandler extends SapphireActionHandler
{

    @Override
    protected Object run( Presentation context )
    {
        IFile file = context.part().getModelElement().adapt( IFile.class );

        if( file != null && file.exists() )
        {
            if( ServiceUIUtil.shouldCreateServiceBuilderJob( file ) )
            {
                BuildWSDDJob job = ServiceCore.createBuildWSDDJob( file.getProject() );

                job.schedule();
            }
        }

        return null;
    }

}
