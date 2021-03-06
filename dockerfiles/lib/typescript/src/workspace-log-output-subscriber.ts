/*
 * Copyright (c) 2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 */

import {MessageBusSubscriber} from './messagebus-subscriber';

/**
 * Class that will display to console all workspace output messages.
 * @author Florent Benoit
 */
export class WorkspaceDisplayOutputMessageBusSubscriber implements MessageBusSubscriber {

    handleMessage(message: string) {
        // maybe parse data to add colors
        console.log(message);
    }

}
