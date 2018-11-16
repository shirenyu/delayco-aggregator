/*
 * Copyright © 2017 shi.INC and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delayco.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.l2switch.arphandler.inventory.InventoryReader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.delaycollector.config.rev140528.DelaycollectorConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.delayco.rev150105.DelaycoService;
import org.opendaylight.yangtools.concepts.Registration;

import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;


public class DelaycoProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DelaycoProvider.class);

    private final DataBroker dataBroker;

    private final PacketProcessingService packetProcessingService;

    private final NotificationProviderService notificationProviderService;
    private final DelaycollectorConfig config;

    private final SalFlowService salFlowService;

    private final RpcProviderRegistry rpcProviderRegistry;

    private static Thread thread;
    private static Registration registration;
    private static Map<String, Long> delayMap=new ConcurrentHashMap<String, Long>();
    private BindingAwareBroker.RpcRegistration<DelaycoService> rpcRegistration;

    public DelaycoProvider(final DataBroker dataBroker, final NotificationProviderService notificationProviderService, final DelaycollectorConfig config, PacketProcessingService packetProcessingService, final SalFlowService salFlowService, RpcProviderRegistry rpcProviderRegistry) {
        this.dataBroker = dataBroker;
        this.notificationProviderService = notificationProviderService;
        this.config = config;
        this.packetProcessingService = packetProcessingService;
        this.salFlowService = salFlowService;
        this.rpcProviderRegistry = rpcProviderRegistry;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        LOG.info("DelaycoProvider Session Initiated");
        InitialFlowWriter initialFlowWriter = new InitialFlowWriter(salFlowService,dataBroker);
        initialFlowWriter.senddate();

        Delay_Sender_Task delay_Sender_Task=new Delay_Sender_Task(config, packetProcessingService, dataBroker);
        thread=new Thread(delay_Sender_Task, "ipv4packetSpeaker");
        thread.start();
        //Notification，监听接收的数据包
        DelayFromIpv4 delayFromIpv4=new DelayFromIpv4(config,delayMap);
        registration=notificationProviderService.registerNotificationListener(delayFromIpv4);

        DelayserviceImpl delayserviceImpl = new DelayserviceImpl(delayMap);
        rpcRegistration = rpcProviderRegistry.addRpcImplementation(DelaycoService.class,delayserviceImpl);


    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        LOG.info("DelaycoProvider Closed");
        thread.stop();
        thread.destroy();
        try{
            registration.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}