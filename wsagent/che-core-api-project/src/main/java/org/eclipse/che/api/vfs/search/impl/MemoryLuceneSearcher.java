/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.api.vfs.search.impl;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.eclipse.che.api.vfs.VirtualFileFilter;

/**
 * In-memory implementation of LuceneSearcher.
 */
public class MemoryLuceneSearcher extends LuceneSearcher {
    MemoryLuceneSearcher(AbstractLuceneSearcherProvider.CloseCallback closeCallback) {
        super(closeCallback);
    }

    MemoryLuceneSearcher(VirtualFileFilter filter, AbstractLuceneSearcherProvider.CloseCallback closeCallback) {
        super(filter, closeCallback);
    }

    @Override
    protected Directory makeDirectory() {
        return new RAMDirectory();
    }
}
