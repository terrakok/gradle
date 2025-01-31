/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.snapshot;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import org.gradle.internal.hash.HashCode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class SnapshotUtil {

    public static Map<String, FileSystemLocationSnapshot> index(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(entrySnapshot -> {
            index.put(entrySnapshot.getAbsolutePath(), entrySnapshot);
            return SnapshotVisitResult.CONTINUE;
        });
        return index;
    }

    public static Map<String, FileSystemLocationSnapshot> rootIndex(FileSystemSnapshot snapshot) {
        HashMap<String, FileSystemLocationSnapshot> index = new HashMap<>();
        snapshot.accept(entrySnapshot -> {
            index.put(entrySnapshot.getAbsolutePath(), entrySnapshot);
            return SnapshotVisitResult.SKIP_SUBTREE;
        });
        return index;
    }

    public static <T extends FileSystemNode> Optional<MetadataSnapshot> getMetadataFromChildren(ChildMap<T> children, VfsRelativePath targetPath, CaseSensitivity caseSensitivity, Supplier<Optional<MetadataSnapshot>> noChildFoundResult) {
        return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<T, Optional<MetadataSnapshot>>() {
            @Override
            public Optional<MetadataSnapshot> handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                return child.getSnapshot(pathInChild, caseSensitivity);
            }

            @Override
            public Optional<MetadataSnapshot> handleAsAncestorOfChild(String childPath, T child) {
                return noChildFoundResult.get();
            }

            @Override
            public Optional<MetadataSnapshot> handleExactMatchWithChild(T child) {
                return child.getSnapshot();
            }

            @Override
            public Optional<MetadataSnapshot> handleUnrelatedToAnyChild() {
                return noChildFoundResult.get();
            }
        });
    }

    public static <T extends FileSystemNode> ReadOnlyFileSystemNode getChild(ChildMap<T> children, VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
        return children.withNode(targetPath, caseSensitivity, new ChildMap.NodeHandler<T, ReadOnlyFileSystemNode>() {
            @Override
            public ReadOnlyFileSystemNode handleAsDescendantOfChild(VfsRelativePath pathInChild, T child) {
                return child.getNode(pathInChild, caseSensitivity);
            }

            @Override
            public ReadOnlyFileSystemNode handleAsAncestorOfChild(String childPath, T child) {
                return new SingleChildReadOnlyFileSystemNode(targetPath.pathToChild(childPath), child);
            }

            @Override
            public ReadOnlyFileSystemNode handleExactMatchWithChild(T child) {
                return child;
            }

            @Override
            public ReadOnlyFileSystemNode handleUnrelatedToAnyChild() {
                return ReadOnlyFileSystemNode.EMPTY;
            }
        });
    }

    public static ImmutableMultimap<String, HashCode> getRootHashes(FileSystemSnapshot roots) {
        if (roots == FileSystemSnapshot.EMPTY) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<String, HashCode> builder = ImmutableListMultimap.builder();
        roots.accept(snapshot -> {
            builder.put(snapshot.getAbsolutePath(), snapshot.getHash());
            return SnapshotVisitResult.SKIP_SUBTREE;
        });
        return builder.build();
    }

    private static class SingleChildReadOnlyFileSystemNode implements ReadOnlyFileSystemNode {
        private final String relativePath;
        private final FileSystemNode child;

        public SingleChildReadOnlyFileSystemNode(String relativePath, FileSystemNode child) {
            this.relativePath = relativePath;
            this.child = child;
        }

        @Override
        public Optional<MetadataSnapshot> getSnapshot(VfsRelativePath targetPath, CaseSensitivity caseSensitivity) {
            return SnapshotUtil.getMetadataFromChildren(getChildren(), targetPath, caseSensitivity, Optional::empty);
        }

        @Override
        public boolean hasDescendants() {
            return child.hasDescendants();
        }

        @Override
        public ReadOnlyFileSystemNode getNode(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
            return getChild(getChildren(), relativePath, caseSensitivity);
        }

        @Override
        public Optional<MetadataSnapshot> getSnapshot() {
            return Optional.empty();
        }

        @Override
        public Stream<FileSystemLocationSnapshot> rootSnapshots() {
            return child.rootSnapshots();
        }

        private SingletonChildMap<FileSystemNode> getChildren() {
            return new SingletonChildMap<>(relativePath, child);
        }
    }
}
