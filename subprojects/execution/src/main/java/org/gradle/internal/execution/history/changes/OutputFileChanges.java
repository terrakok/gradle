/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.execution.history.changes;

import com.google.common.collect.Interner;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.RelativePathFingerprintingStrategy;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;

public class OutputFileChanges implements ChangeContainer {

    private static final Interner<String> NOOP_STRING_INTERNER = sample -> sample;

    private final SortedMap<String, FileSystemSnapshot> previous;
    private final SortedMap<String, FileSystemSnapshot> current;

    public OutputFileChanges(SortedMap<String, FileSystemSnapshot> previous, SortedMap<String, FileSystemSnapshot> current) {
        this.previous = previous;
        this.current = current;
    }

    @Override
    public boolean accept(ChangeVisitor visitor) {
        return SortedMapDiffUtil.diff(previous, current, new PropertyDiffListener<String, FileSystemSnapshot, FileSystemSnapshot>() {
            @Override
            public boolean removed(String previousProperty) {
                return true;
            }

            @Override
            public boolean added(String currentProperty) {
                return true;
            }

            @Override
            public boolean updated(String property, FileSystemSnapshot previous, FileSystemSnapshot current) {
                if (previous == current) {
                    return true;
                }
                if (previous instanceof FileSystemLocationSnapshot && current instanceof FileSystemLocationSnapshot) {
                    FileSystemLocationSnapshot previousLocationSnapshot = (FileSystemLocationSnapshot) previous;
                    FileSystemLocationSnapshot currentLocationSnapshot = (FileSystemLocationSnapshot) current;
                    if (previousLocationSnapshot.getHash().equals(currentLocationSnapshot.getHash())) {
                        // As with relative path, we compare the name of the roots if they are regular files.
                        if (previousLocationSnapshot.getType() != FileType.RegularFile
                            || previousLocationSnapshot.getName().equals(currentLocationSnapshot.getName())) {
                            return true;
                        }
                    }
                }
                RelativePathFingerprintingStrategy relativePathFingerprintingStrategy = new RelativePathFingerprintingStrategy(NOOP_STRING_INTERNER, DirectorySensitivity.DEFAULT);
                CurrentFileCollectionFingerprint previousFingerprint = DefaultCurrentFileCollectionFingerprint.from(previous, relativePathFingerprintingStrategy, null);
                CurrentFileCollectionFingerprint currentFingerprint = DefaultCurrentFileCollectionFingerprint.from(current, relativePathFingerprintingStrategy, null);
                return NormalizedPathFingerprintCompareStrategy.INSTANCE.visitChangesSince(previousFingerprint,
                    currentFingerprint,
                    "Output property '" + property + "'",
                    visitor);
            }
        });
    }

    private static Map<String, FileSystemLocationSnapshot> index(FileSystemSnapshot snapshot) {
        Map<String, FileSystemLocationSnapshot> index = new LinkedHashMap<>();
        snapshot.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
                // Remove missing roots so they show up as added/removed instead of changed
                if (!(isRoot && snapshot instanceof MissingFileSnapshot)) {
                    index.put(snapshot.getAbsolutePath(), snapshot);
                }
                return SnapshotVisitResult.CONTINUE;
            }
        });
        return index;
    }
}
