/**
 * Copyright (c) 2012, s3auth.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the s3auth.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.s3auth.hosts;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodb.AmazonDynamoDB;
import com.amazonaws.services.dynamodb.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodb.model.AttributeValue;
import com.amazonaws.services.dynamodb.model.DeleteItemRequest;
import com.amazonaws.services.dynamodb.model.DeleteItemResult;
import com.amazonaws.services.dynamodb.model.Key;
import com.amazonaws.services.dynamodb.model.PutItemRequest;
import com.amazonaws.services.dynamodb.model.PutItemResult;
import com.amazonaws.services.dynamodb.model.ScanRequest;
import com.amazonaws.services.dynamodb.model.ScanResult;
import com.jcabi.log.Logger;
import com.rexsl.core.Manifests;
import java.io.Closeable;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Abstraction on top of DynamoDB SDK.
 *
 * <p>The class is immutable and thread-safe.
 *
 * @author Yegor Bugayenko (yegor@tpc2.com)
 * @version $Id$
 * @since 0.0.1
 * @checkstyle ClassDataAbstractionCoupling (500 lines)
 * @checkstyle MultipleStringLiterals (500 lines)
 */
@SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
public final class Dynamo implements Closeable {

    /**
     * AWS client.
     */
    private final transient AmazonDynamoDB client;

    /**
     * Table name.
     */
    private final transient String table =
        Manifests.read("S3Auth-AwsDynamoTable");

    /**
     * Public ctor.
     */
    public Dynamo() {
        this.client = new AmazonDynamoDBClient(
            new BasicAWSCredentials(
                Manifests.read("S3Auth-AwsDynamoKey"),
                Manifests.read("S3Auth-AwsDynamoSecret")
            )
        );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        this.client.shutdown();
    }

    /**
     * Load all data from DynamoDB.
     * @return Map of users and their domains
     * @throws IOException If some IO problem inside
     */
    public ConcurrentMap<String, Set<Domain>> load() throws IOException {
        final ConcurrentMap<String, Set<Domain>> domains =
            new ConcurrentHashMap<String, Set<Domain>>();
        final ScanResult result = this.client.scan(new ScanRequest(this.table));
        for (final Map<String, AttributeValue> item : result.getItems()) {
            final String user = item.get("user.identity").getS();
            domains.putIfAbsent(user, new HashSet<Domain>());
            domains.get(user).add(
                new DefaultDomain(
                    item.get("domain.name").getS(),
                    item.get("domain.key").getS(),
                    item.get("domain.secret").getS()
                )
            );
        }
        Logger.info(this, "#load(): %d items", domains.size());
        return domains;
    }

    /**
     * Save to DynamoDB.
     * @param user Who is the owner
     * @param domain The domain to save
     * @throws IOException If some IO problem inside
     */
    public void add(final String user, final Domain domain) throws IOException {
        final ConcurrentMap<String, AttributeValue> attrs =
            new ConcurrentHashMap<String, AttributeValue>();
        attrs.put("user.identity", new AttributeValue(user));
        attrs.put("domain.name", new AttributeValue(domain.name()));
        attrs.put("domain.key", new AttributeValue(domain.key()));
        attrs.put("domain.secret", new AttributeValue(domain.secret()));
        final PutItemResult result = this.client.putItem(
            new PutItemRequest(this.table, attrs)
        );
        Logger.info(
            this,
            "#add('%s', '%s'): added, %.2f units",
            user,
            domain.name(),
            result.getConsumedCapacityUnits()
        );
    }

    /**
     * Delete from DynamoDB.
     * @param domain The domain to save
     * @throws IOException If some IO problem inside
     */
    public void remove(final Domain domain) throws IOException {
        final DeleteItemResult result = this.client.deleteItem(
            new DeleteItemRequest(
                this.table, new Key(new AttributeValue(domain.name()))
            )
        );
        Logger.info(
            this,
            "#remove('%s'): removed, %.2f units",
            domain.name(),
            result.getConsumedCapacityUnits()
        );
    }

}
