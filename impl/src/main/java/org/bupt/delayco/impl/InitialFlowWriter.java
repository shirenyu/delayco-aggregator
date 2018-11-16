/*
 * Copyright (c) 2014, 2015 Cisco Systems, Inc., Brocade Communications Systems, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.bupt.delayco.impl;

import com.google.common.collect.ImmutableList;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.l2switch.arphandler.inventory.InventoryReader;
import org.opendaylight.openflowplugin.api.OFConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.FlowTableRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.MatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l2.types.rev130827.EtherType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.ethernet.match.fields.EthernetTypeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.match.types.rev131026.match.EthernetMatchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ethernet.rev140528.KnownEtherType;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adds a flow, which sends all ARP packets to the controller, on all switches.
 * Registers as ODL Inventory listener so that it can add flows once a new node i.e. switch is added
 */
public class InitialFlowWriter {
    private static final Logger LOG = LoggerFactory.getLogger(org.opendaylight.l2switch.arphandler.flow.InitialFlowWriter.class);

    private final ExecutorService initialFlowExecutor = Executors.newCachedThreadPool();
    private final SalFlowService salFlowService;
    private final String FLOW_ID_PREFIX = "L2switch-";
    private short flowTableId = 0;
    private int flowPriority = 4;
    private int flowIdleTimeout = 0;
    private int flowHardTimeout = 0;

    private AtomicLong flowIdInc = new AtomicLong();
    private AtomicLong flowCookieInc = new AtomicLong(0x2b00000000000000L);
    private final DataBroker dataservice;


    public InitialFlowWriter(SalFlowService salFlowService,DataBroker dataSerivice) {
        this.salFlowService = salFlowService;
        this.dataservice = dataSerivice;
    }




    public void senddate ()
    {
        InventoryReader inventoryReader=new InventoryReader(dataservice);
        inventoryReader.setRefreshData(true);
        inventoryReader.readInventory();

        HashMap<String, NodeConnectorRef> nodeConnectorMap=inventoryReader.getControllerSwitchConnectors();
        for(String nodeid:nodeConnectorMap.keySet()){
            InstanceIdentifier  nodeII = InstanceIdentifier.builder(Nodes.class)
                    .child(Node.class, new NodeKey(new NodeId(nodeid))).build();
            initialFlowExecutor.submit(new InitialFlowWriterProcessor(nodeII));
        }

//        InstanceIdentifier  nodeII = InstanceIdentifier.builder(Nodes.class)
//                .child(Node.class, new NodeKey(new NodeId("openflow_set"))).build();
//        initialFlowExecutor.submit(new InitialFlowWriterProcessor(nodeII));
    }


    /**
     * A private class to process the node updated event in separate thread. Allows to release the
     * thread that invoked the data node updated event. Avoids any thread lock it may cause.
     */
    private class InitialFlowWriterProcessor implements Runnable {
        InstanceIdentifier<Node> nodeId;

        public InitialFlowWriterProcessor(InstanceIdentifier<Node> nodeId) {
            this.nodeId = nodeId;
        }

        @Override
        public void run() {
            addInitialFlows(nodeId);
        }

        /**
         * Adds a flow, which sends all ipv4 packets to the controller, to the specified node.
         * @param nodeId The node to write the flow on.
         */
        public void addInitialFlows(InstanceIdentifier<Node> nodeId) {
            LOG.debug("adding initial flows for node {} ", nodeId);

            InstanceIdentifier<Table> tableId = getTableInstanceId(nodeId);
            InstanceIdentifier<Flow> flowId = getFlowInstanceId(tableId);

            //add arpToController flow
            writeFlowToController(nodeId, tableId, flowId, createArpToControllerFlow(flowTableId, flowPriority));
            LOG.debug("Added initial flows for node {} ", nodeId);
        }

        private InstanceIdentifier<Table> getTableInstanceId(InstanceIdentifier<Node> nodeId) {
            // get flow table keywriteFlowToController
            TableKey flowTableKey = new TableKey(flowTableId);

            return nodeId.builder()
                    .augmentation(FlowCapableNode.class)
                    .child(Table.class, flowTableKey)
                    .build();
        }

        private InstanceIdentifier<Flow> getFlowInstanceId(InstanceIdentifier<Table> tableId) {
            // generate unique flow key
            FlowId flowId = new FlowId(FLOW_ID_PREFIX+String.valueOf(flowIdInc.getAndIncrement()));
            FlowKey flowKey = new FlowKey(flowId);
            return tableId.child(Flow.class, flowKey);
        }

        private Flow createArpToControllerFlow(Short tableId, int priority) {

            // start building flow
            FlowBuilder ipv4Flow = new FlowBuilder() //
                    .setTableId(tableId) //
                    .setFlowName("arptocntrl");

            // use its own hash code for id.
            ipv4Flow.setId(new FlowId(Long.toString(ipv4Flow.hashCode())));
            EthernetMatchBuilder ethernetMatchBuilder = new EthernetMatchBuilder()
                    .setEthernetType(new EthernetTypeBuilder()
                            .setType(new EtherType(Long.valueOf(KnownEtherType.Ipv4.getIntValue()))).build());

            Match match = new MatchBuilder()
                    .setEthernetMatch(ethernetMatchBuilder.build())
                    .build();

            List<Action> actions = new ArrayList<Action>();
            actions.add(getSendToControllerAction());


            // Create an Apply Action
            ApplyActions applyActions = new ApplyActionsBuilder() //
                    .setAction(ImmutableList.copyOf(actions)) //
                    .build();

            // Wrap our Apply Action in an Instruction
            Instruction applyActionsInstruction = new InstructionBuilder() //
                    .setOrder(0)
                    .setInstruction(new ApplyActionsCaseBuilder()//
                            .setApplyActions(applyActions) //
                            .build()) //
                    .build();

            // Put our Instruction in a list of Instructions
            ipv4Flow
                    .setMatch(match) //
                    .setInstructions(new InstructionsBuilder() //
                            .setInstruction(ImmutableList.of(applyActionsInstruction)) //
                            .build()) //
                    .setPriority(priority) //
                    .setBufferId(OFConstants.OFP_NO_BUFFER) //
                    .setHardTimeout(flowHardTimeout) //
                    .setIdleTimeout(flowIdleTimeout) //
                    .setCookie(new FlowCookie(BigInteger.valueOf(flowCookieInc.getAndIncrement())))
                    .setFlags(new FlowModFlags(false, false, false, false, false));

            return ipv4Flow.build();
        }

        private Action getSendToControllerAction() {
            Action sendToController = new ActionBuilder()
                    .setOrder(0)
                    .setKey(new ActionKey(0))
                    .setAction(new OutputActionCaseBuilder()
                            .setOutputAction(new OutputActionBuilder()
                                    .setMaxLength(0xffff)
                                    .setOutputNodeConnector(new Uri(OutputPortValues.CONTROLLER.toString()))
                                    .build())
                            .build())
                    .build();
            return sendToController;
        }


        private Future<RpcResult<AddFlowOutput>> writeFlowToController(InstanceIdentifier<Node> nodeInstanceId,
                                                                       InstanceIdentifier<Table> tableInstanceId,
                                                                       InstanceIdentifier<Flow> flowPath,
                                                                       Flow flow) {
            LOG.trace("Adding flow to node {}",nodeInstanceId.firstKeyOf(Node.class, NodeKey.class).getId().getValue());
            final AddFlowInputBuilder builder = new AddFlowInputBuilder(flow);
            builder.setNode(new NodeRef(nodeInstanceId));
            builder.setFlowRef(new FlowRef(flowPath));
            builder.setFlowTable(new FlowTableRef(tableInstanceId));
            builder.setTransactionUri(new Uri(flow.getId().getValue()));
            return salFlowService.addFlow(builder.build());
        }
    }
}
