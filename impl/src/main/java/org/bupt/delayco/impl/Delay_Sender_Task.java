/*
 * Copyright (c) 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.delayco.impl;

import org.bupt.delayco.impl.util.IPv4;
import org.opendaylight.controller.liblldp.*;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.l2switch.arphandler.core.PacketDispatcher;
import org.opendaylight.l2switch.arphandler.inventory.InventoryReader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.output.action._case.OutputActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.ActionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.OutputPortValues;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.delaycollector.config.rev140528.DelaycollectorConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.ipv4.rev140528.KnownIpProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.TransmitPacketInputBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Delay_Sender_Task implements Runnable{
	private static final Logger LOG = LoggerFactory.getLogger(Delay_Sender_Task.class);

	private final DelaycollectorConfig delaycollectorConfig;
	private final PacketProcessingService packetProcessingService;
	private final DataBroker dataservice;
	public Delay_Sender_Task(DelaycollectorConfig delayCollectorConfig, PacketProcessingService packetProcessingService, DataBroker dataSerivice){
		this.delaycollectorConfig=delayCollectorConfig;
		this.packetProcessingService=packetProcessingService;
		this.dataservice=dataSerivice;
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		PacketDispatcher packetDispatcher=new PacketDispatcher();
		packetDispatcher.setPacketProcessingService(packetProcessingService);
		InventoryReader inventoryReader=new InventoryReader(dataservice);
		inventoryReader.setRefreshData(true);
		inventoryReader.readInventory();
		while(delaycollectorConfig.isIsActive()){
			//generate a ipv4 packet
			IPv4 iPv4=new IPv4();
			iPv4.setTtl((byte)1).setProtocol((byte)KnownIpProtocols.Experimentation1.getIntValue());
			try {
				iPv4.setSourceAddress(InetAddress.getByName("0.0.0.1")).setDestinationAddress(InetAddress.getByName("0.0.0.2"));
				iPv4.setOptions(BitBufferHelper.toByteArray(System.nanoTime()));

				packetDispatcher.setInventoryReader(inventoryReader);
				HashMap<String, NodeConnectorRef> nodeConnectorMap=inventoryReader.getControllerSwitchConnectors();
				for(String nodeid:nodeConnectorMap.keySet()){
					iPv4.setVersion((byte)Integer.parseInt(nodeid.split(":")[1])); //存放nodeid，识别从哪个switch发出
					//generate a ethernet packet
					Ethernet ethernet=new Ethernet();
					EthernetAddress srcMac=new EthernetAddress(new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xee});
					EthernetAddress destMac=new EthernetAddress(new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xef});
					ethernet.setSourceMACAddress(srcMac.getValue()).setDestinationMACAddress(destMac.getValue());
					ethernet.setEtherType(EtherTypes.IPv4.shortValue());
					ethernet.setPayload(iPv4);

					packetDispatcher.floodPacket(nodeid, ethernet.serialize(), nodeConnectorMap.get(nodeid), null);
					LOG.info(nodeid + " success ipv4.");
				}
			} catch (ConstructionException | UnknownHostException | PacketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//generate a echo-like packet
			IPv4 echolik = new IPv4();
			echolik.setTtl((byte)1).setProtocol((byte)KnownIpProtocols.Experimentation2.getIntValue());
			try {
				echolik.setSourceAddress(InetAddress.getByName("0.0.0.3")).setDestinationAddress(InetAddress.getByName("0.0.0.4"));
				echolik.setOptions(BitBufferHelper.toByteArray(System.nanoTime()));
				//generate a ethernet packet
				Ethernet ethernet1=new Ethernet();
				EthernetAddress srcMac=new EthernetAddress(new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xee});
				EthernetAddress destMac=new EthernetAddress(new byte[]{(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0xef});
				ethernet1.setSourceMACAddress(srcMac.getValue()).setDestinationMACAddress(destMac.getValue());
				ethernet1.setEtherType(EtherTypes.IPv4.shortValue());
				ethernet1.setPayload(echolik);
				HashMap<String, NodeConnectorRef> nodeConnectorMap=inventoryReader.getControllerSwitchConnectors();
				for(String nodeid:nodeConnectorMap.keySet()){

					List<Action> actionList = new ArrayList<Action>();
					actionList.add(getSendToControllerAction());

					InstanceIdentifier<Node> egressNodePath = nodeConnectorMap.get(nodeid).getValue().firstIdentifierOf(Node.class);
					TransmitPacketInput input = new TransmitPacketInputBuilder() //
							.setPayload(ethernet1.serialize()) //
							.setNode(new NodeRef(egressNodePath)) //
							.setEgress(nodeConnectorMap.get(nodeid)) //
							.setIngress(nodeConnectorMap.get(nodeid)) //
							.setAction(actionList)
							.build();
					packetProcessingService.transmitPacket(input);
					LOG.info(nodeid + " success echo.");
				}

			} catch (UnknownHostException | ConstructionException |PacketException e) {
				e.printStackTrace();
			}

			try {
				Thread.sleep(delaycollectorConfig.getQuerryDelay()*100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
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
}
