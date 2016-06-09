/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.test.arquillian.ce.decisionserver;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.jboss.arquillian.ce.api.OpenShiftResource;
import org.jboss.arquillian.ce.api.OpenShiftResources;
import org.jboss.arquillian.ce.api.Template;
import org.jboss.arquillian.ce.api.TemplateParameter;
import org.jboss.arquillian.ce.cube.RouteURL;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.command.BatchExecutionCommand;
import org.kie.api.command.Command;
import org.kie.api.runtime.ExecutionResults;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.QueryResultsRow;
import org.kie.internal.command.CommandFactory;
import org.kie.server.api.marshalling.Marshaller;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.client.KieServicesClient;
import org.openshift.quickstarts.decisionserver.hellorules.Props;
import org.openshift.quickstarts.decisionserver.hellorules.PropsName;


/**
 * @author Ales Justin
 */
@RunWith(Arquillian.class)
@Template(url = "https://raw.githubusercontent.com/alesj/application-templates/ips_props/decisionserver/decisionserver62-basic-s2i.json",
    parameters = {
        @TemplateParameter(name = "KIE_SERVER_USER", value = "${kie.username:kieserver}"),
        @TemplateParameter(name = "KIE_SERVER_PASSWORD", value = "${kie.password:Redhat@123}"),
        @TemplateParameter(name = "IMAGE_STREAM_NAMESPACE", value = "${kubernetes.namespace}")
    }
)
@OpenShiftResources({
    @OpenShiftResource("classpath:decisionserver-service-account.json"),
    @OpenShiftResource("classpath:decisionserver-app-secret.json"),
    @OpenShiftResource("classpath:decisionserver-internal-imagestream.json")
})
public class DecisionServerPropsTest extends DecisionServerTestBase {

    @RouteURL("kie-app")
    private URL routeURL;

    @Override
    protected URL getRouteURL() {
        return routeURL;
    }

    /*
     * Return the batch command used to fire rules
     */
    public BatchExecutionCommand batchCommand() {
        List<Command<?>> commands = new ArrayList<>();
        commands.add((Command<?>) CommandFactory.newInsert(new PropsName("some.properties")));
        commands.add((Command<?>) CommandFactory.newFireAllRules());
        commands.add((Command<?>) CommandFactory.newQuery("propz", "get properties"));
        return CommandFactory.newBatchExecution(commands, "HelloRulesSession");
    }

    @Test
    public void testProps() throws Exception {
        log.info("Running test props");
        // for untrusted connections
        prepareClientInvocation();

        KieServicesClient client = getKieRestServiceClient(getRouteURL());

        ServiceResponse<String> response = executeCommands(client, "HelloRulesContainer", batchCommand());
        Assert.assertNotNull("Null response", response);

        Marshaller marshaller = getMarshaller(getClasses(), MarshallingFormat.XSTREAM, Props.class.getClassLoader());
        ExecutionResults results = marshaller.unmarshall(response.getResult(), ExecutionResults.class);

        // results cannot be null
        Assert.assertNotNull(results);

        QueryResults queryResults = (QueryResults) results.getValue("propz");
        Props props = null;
        for (QueryResultsRow queryResult : queryResults) {
            props = (Props) queryResult.get("props");
        }

        Assert.assertNotNull(props);
//        Assert.assertFalse(props.getProperties().isEmpty());
//        Assert.assertEquals("properties", props.getProperties().getProperty("some"));
    }
}
