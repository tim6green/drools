package org.drools.core.reteoo;

import org.drools.core.base.ClassObjectType;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.EmptyBetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContextFactory;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.drools.core.impl.InternalKnowledgeBase;
import org.drools.core.impl.StatefulKnowledgeSessionImpl;
import org.drools.core.phreak.PhreakNotNode;
import org.drools.core.phreak.SegmentUtilities;
import org.drools.core.reteoo.LeftInputAdapterNode.LiaNodeMemory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.spi.PropagationContext;
import org.junit.Test;
import org.kie.api.KieBaseConfiguration;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.conf.RuleEngineOption;

import static org.junit.Assert.*;

public class NodeSegmentUnlinkingTest {
    InternalKnowledgeBase kBase;
    BuildContext         buildContext;
    PropagationContext   context;

    LeftInputAdapterNode liaNode;
    BetaNode             n1;
    BetaNode             n2;
    BetaNode             n3;
    BetaNode             n4;
    BetaNode             n5;
    BetaNode             n6;
    BetaNode             n7;
    BetaNode             n8;
    RuleImpl             rule1;
    RuleImpl             rule2;
    RuleImpl             rule3;
    RuleImpl             rule4;
    RuleImpl             rule5;

    static final int     JOIN_NODE   = 0;
    static final int     EXISTS_NODE = 1;
    static final int     NOT_NODE    = 2;

    private BetaNode createBetaNode(int id,
                                    int type,
                                    LeftTupleSource leftTupleSource) {
        MockObjectSource mockObjectSource = new MockObjectSource( 8 );

        BetaNode betaNode = null;
        switch ( type ) {
            case JOIN_NODE : {
                betaNode = new JoinNode( id, leftTupleSource, mockObjectSource, new EmptyBetaConstraints(), buildContext );
                break;
            }
            case EXISTS_NODE : {
                betaNode = new ExistsNode( id, leftTupleSource, mockObjectSource, new EmptyBetaConstraints(), buildContext );
                break;
            }
            case NOT_NODE : {
                betaNode = new NotNode( id, leftTupleSource, mockObjectSource, new EmptyBetaConstraints(), buildContext );
                break;
            }
        }

        mockObjectSource.attach();
        betaNode.attach();

        return betaNode;
    }

    public void setUp(int type) {
        setUp(new int[] { type, type, type, type, type, type, type, type } );
    }
    
    public void setUp(int... type) {
        KieBaseConfiguration kconf = org.kie.internal.KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        buildContext = new BuildContext( kBase, kBase.getReteooBuilder().getIdGenerator() );

        PropagationContextFactory pctxFactory = kBase.getConfiguration().getComponentFactory().getPropagationContextFactory();
        context = pctxFactory.createPropagationContext(0, PropagationContext.INSERTION, null, null, null);

        MockTupleSource mockTupleSource = new MockTupleSource( 9 );

        rule1 = new RuleImpl( "rule1" );
        rule2 = new RuleImpl( "rule2" );
        rule3 = new RuleImpl( "rule3" );
        
        ObjectTypeNode otn = new ObjectTypeNode( 3, null, new ClassObjectType( String.class ), buildContext );
        liaNode = new LeftInputAdapterNode(4, otn, buildContext );
        
        // 3, 4, 5, 6 are in same shared segment
        n1 = createBetaNode( 10, type[0], liaNode );
        n2 = createBetaNode( 11, type[1], n1 );
        RuleTerminalNode rtn1 = new RuleTerminalNode( 18,
                                                      n2,
                                                      rule1,
                                                      rule1.getLhs(),
                                                      0,
                                                      buildContext );
        rtn1.attach();
        
        
        n3 = createBetaNode( 12, type[2], n1 );
        n4 = createBetaNode( 13, type[3], n3 );
        n5 = createBetaNode( 14, type[4], n4 );
        n6 = createBetaNode( 15, type[5], n5 ); 
        RuleTerminalNode rtn2 = new RuleTerminalNode( 19,
                                                      n6,
                                                      rule2,
                                                      rule2.getLhs(),
                                                      0,
                                                      buildContext );
        rtn2.attach();       

        n7 = createBetaNode( 16, type[6], n6 );
        n8 = createBetaNode( 17, type[7], n7 );
        RuleTerminalNode rtn3 = new RuleTerminalNode( 20,
                                                      n8,
                                                      rule3,
                                                      rule3.getLhs(),
                                                      0,
                                                      buildContext );
        rtn3.attach(); 
        
        // n1 -> n2 -> r1
        //  \ 
        //   n3 -> n4 -> n5 -> n6 -> r2
        //                      \
        //                      n7 -> n8 -> r3          
        
        n1.getAssociations().put( rule1, null );
        n1.getAssociations().put( rule2, null );
        n1.getAssociations().put( rule3, null );
        n2.getAssociations().put( rule1, null );
        n2.getAssociations().put( rule2, null );
        n2.getAssociations().put( rule3, null );

        n3.getAssociations().put( rule2, null );
        n3.getAssociations().put( rule3, null );
        n4.getAssociations().put( rule2, null );
        n4.getAssociations().put( rule3, null );
        n5.getAssociations().put( rule2, null );
        n5.getAssociations().put( rule3, null );
        n6.getAssociations().put( rule2, null );
        n6.getAssociations().put( rule3, null );

        n7.getAssociations().put( rule3, null );
        n8.getAssociations().put( rule3, null );
    }

    @Test
    public void testSingleNodeinSegment() {

        rule1 = new RuleImpl( "rule1" );
        rule2 = new RuleImpl( "rule2" );
        rule3 = new RuleImpl( "rule3" );

        KieBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        BuildContext buildContext = new BuildContext( kBase, kBase.getReteooBuilder().getIdGenerator() );

        MockObjectSource mockObjectSource = new MockObjectSource( 8 );
        MockTupleSource mockTupleSource = new MockTupleSource( 9 );

        // n2 is only node in it's segment
        ObjectTypeNode otn = new ObjectTypeNode( 2, null, new ClassObjectType( String.class ), buildContext );
        BetaNode n1 = new JoinNode( 10, new LeftInputAdapterNode(3, otn, buildContext ), mockObjectSource,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n2 = new JoinNode( 11, n1, mockObjectSource,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n3 = new JoinNode( 12, n1, mockObjectSource,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n4 = new JoinNode( 13, n2, mockObjectSource,
                                    new EmptyBetaConstraints(), buildContext );
        BetaNode n5 = new JoinNode( 14, n2, mockObjectSource,
                                    new EmptyBetaConstraints(), buildContext );

        n1.addAssociation( rule1, null );
        n1.addAssociation( rule2, null );
        n1.addAssociation( rule3, null );

        n2.addAssociation( rule2, null );
        n2.addAssociation( rule3, null );

        n3.addAssociation( rule1, null );
        n4.addAssociation( rule2, null );
        n5.addAssociation( rule3, null );

        mockObjectSource.attach();
        mockTupleSource.attach();
        n1.attach();
        n2.attach();
        n3.attach();
        n4.attach();
        n5.attach();

        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();
        createSegmentMemory( n2, ksession );

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory( n1 );
        assertNull( bm.getSegmentMemory() );

        bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertNull( bm.getSegmentMemory() );

        bm = (BetaMemory) ksession.getNodeMemory( n4 );
        assertNull( bm.getSegmentMemory() );

        bm = (BetaMemory) ksession.getNodeMemory( n2 );
        assertEquals( 1, bm.getNodePosMaskBit() );
        assertEquals( 1, bm.getSegmentMemory().getAllLinkedMaskTest() );
    }
    
    @Test
    public void testLiaNodeInitialisation() {
        setUp( JOIN_NODE );
        // Initialise from lian
        KieBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );

        InternalKnowledgeBase kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        SegmentUtilities.createSegmentMemory( liaNode, ksession );
        liaNode.assertObject((InternalFactHandle) ksession.insert("str"), context, ksession);
        

        LiaNodeMemory liaMem = (LiaNodeMemory) ksession.getNodeMemory( liaNode );
        assertEquals( 1, liaMem.getNodePosMaskBit() );
        assertEquals( 3, liaMem.getSegmentMemory().getAllLinkedMaskTest() ); 
        
        BetaMemory bm1 = (BetaMemory) ksession.getNodeMemory( n1 );
        assertEquals( 2, bm1.getNodePosMaskBit() );
        assertEquals( 3, bm1.getSegmentMemory().getAllLinkedMaskTest() );         
        
        // Initialise from n1     
        kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        n1.assertObject( (InternalFactHandle) ksession.insert( "str" ), context, ksession );
        

        liaMem = (LiaNodeMemory) ksession.getNodeMemory( liaNode );
        assertEquals( 1, liaMem.getNodePosMaskBit() );
        assertEquals( 3, liaMem.getSegmentMemory().getAllLinkedMaskTest() ); 
        
        bm1 = (BetaMemory) ksession.getNodeMemory( n1 );
        assertEquals( 2, bm1.getNodePosMaskBit() );
        assertEquals( 3, bm1.getSegmentMemory().getAllLinkedMaskTest() );           
    }
    
    @Test
    public void testLiaNodeLinking() {
        setUp( JOIN_NODE );
        // Initialise from lian
        KieBaseConfiguration kconf = org.kie.internal.KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        SegmentUtilities.createSegmentMemory( liaNode, ksession );
        
        InternalFactHandle fh1 = (InternalFactHandle) ksession.insert( "str1" );
        n1.assertObject( fh1, context, ksession );
        
        LiaNodeMemory liaMem = (LiaNodeMemory) ksession.getNodeMemory( liaNode );
        assertEquals( 1, liaMem.getNodePosMaskBit() );
        assertEquals( 3, liaMem.getSegmentMemory().getAllLinkedMaskTest() ); 
        
        BetaMemory bm1 = (BetaMemory) ksession.getNodeMemory( n1 );
        assertEquals( 2, bm1.getNodePosMaskBit() );
        assertEquals( 3, bm1.getSegmentMemory().getAllLinkedMaskTest() );     
        
        // still unlinked
        assertFalse( liaMem.getSegmentMemory().isSegmentLinked() );
        
        // now linked
        InternalFactHandle fh2 = (InternalFactHandle) ksession.insert( "str2" );
        liaNode.assertObject( fh2, context, ksession );
        assertTrue( liaMem.getSegmentMemory().isSegmentLinked() );
        
        // test unlink after one retract
        liaNode.retractLeftTuple( fh2.getFirstLeftTuple(), context, ksession );
        assertFalse( liaMem.getSegmentMemory().isSegmentLinked() );
        
        // check counter, after multiple asserts
        InternalFactHandle fh3 = (InternalFactHandle) ksession.insert( "str3" );
        InternalFactHandle fh4 = (InternalFactHandle) ksession.insert( "str4" );
        liaNode.assertObject( fh3, context, ksession );
        liaNode.assertObject( fh4, context, ksession );
        
        assertTrue( liaMem.getSegmentMemory().isSegmentLinked() );
        
        liaNode.retractLeftTuple( fh3.getFirstLeftTuple(), context, ksession );
        assertTrue( liaMem.getSegmentMemory().isSegmentLinked() );

        liaNode.retractLeftTuple( fh4.getFirstLeftTuple(), context, ksession );
        assertFalse( liaMem.getSegmentMemory().isSegmentLinked() );
    }

    @Test
    public void tesMultiNodeSegmentDifferentInitialisationPoints() {
        setUp( JOIN_NODE );
        // Initialise from n3
        KieBaseConfiguration kconf = org.kie.internal.KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        createSegmentMemory(n3, ksession);

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory( n1 );
        assertNull(bm.getSegmentMemory());

        bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertEquals( 1, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n4 );
        assertEquals( 2, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n5 );
        assertEquals( 4, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n6 );
        assertEquals( 8, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        // Initialise from n4       
        kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        bm = createSegmentMemory( n4, ksession );

        bm = (BetaMemory) ksession.getNodeMemory( n1 );
        assertNull( bm.getSegmentMemory() );

        bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertEquals( 1, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n4 );
        assertEquals( 2, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n5 );
        assertEquals( 4, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n6 );
        assertEquals(8, bm.getNodePosMaskBit());
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        // Initialise from n5
        kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        createSegmentMemory( n5, ksession );

        bm = (BetaMemory) ksession.getNodeMemory( n1 );
        assertNull(bm.getSegmentMemory());

        bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertEquals( 1, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n4 );
        assertEquals( 2, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n5 );
        assertEquals( 4, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n6 );
        assertEquals( 8, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        // Initialise from n6
        kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        createSegmentMemory( n6, ksession );

        bm = (BetaMemory) ksession.getNodeMemory( n1 );
        assertNull( bm.getSegmentMemory() );

        bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertEquals( 1, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n4 );
        assertEquals( 2, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n5 );
        assertEquals( 4, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );

        bm = (BetaMemory) ksession.getNodeMemory( n6 );
        assertEquals( 8, bm.getNodePosMaskBit() );
        assertEquals( 15, bm.getSegmentMemory().getAllLinkedMaskTest() );
    }

    @Test
    public void testAllLinkedInWithJoinNodesOnly() {
        setUp( JOIN_NODE );

        assertEquals( JoinNode.class, n3.getClass() ); // make sure it created JoinNodes

        KieBaseConfiguration kconf = org.kie.internal.KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        DefaultFactHandle f1 = (DefaultFactHandle) ksession.insert( "test1" );
        n3.assertObject( f1, context, ksession );

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );

        n4.assertObject( f1, context, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );

        n5.assertObject( f1, context, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );

        n6.assertObject( f1, context, ksession );
        assertTrue( bm.getSegmentMemory().isSegmentLinked() ); // only after all 4 nodes are populated, is the segment linked in

        n6.retractRightTuple( f1.getLastRightTuple(), context, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() ); // check retraction unlinks again
    }

    @Test
    public void testAllLinkedInWithExistsNodesOnly() {
        setUp( EXISTS_NODE );

        assertEquals( ExistsNode.class, n3.getClass() ); // make sure it created ExistsNodes

        KieBaseConfiguration kconf = org.kie.internal.KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        DefaultFactHandle f1 = (DefaultFactHandle) ksession.insert( "test1" );
        n3.assertObject( f1, context, ksession );

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory( n3 );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );

        n4.assertObject( f1, context, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );

        n5.assertObject( f1, context, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );

        n6.assertObject( f1, context, ksession );
        assertTrue( bm.getSegmentMemory().isSegmentLinked() ); // only after all 4 nodes are populated, is the segment linked in

        n6.retractRightTuple( f1.getLastRightTuple(), context, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() ); // check retraction unlinks again        
    }

    private static BetaMemory createSegmentMemory(BetaNode node,
                                                  InternalWorkingMemory wm) {
        BetaMemory betaMemory = (BetaMemory) wm.getNodeMemory( node );
        if ( betaMemory.getSegmentMemory() == null ) {
            SegmentUtilities.createSegmentMemory( node, wm );
        }
        return betaMemory;

    }

    @Test
    public void testAllLinkedInWithNotNodesOnly() {
        setUp( NOT_NODE );

        assertEquals( NotNode.class, n3.getClass() ); // make sure it created NotNodes

        KieBaseConfiguration kconf = org.kie.internal.KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( RuleEngineOption.PHREAK );
        InternalKnowledgeBase kBase = (InternalKnowledgeBase) KnowledgeBaseFactory.newKnowledgeBase(kconf);
        StatefulKnowledgeSessionImpl ksession = (StatefulKnowledgeSessionImpl)kBase.newStatefulKnowledgeSession();

        BetaMemory bm = (BetaMemory) ksession.getNodeMemory( n3 );
        createSegmentMemory( n3, ksession );
        assertTrue( bm.getSegmentMemory().isSegmentLinked() ); // not nodes start off linked

        DefaultFactHandle f1 = (DefaultFactHandle) ksession.insert( "test1" ); // unlinked after first assertion
        n3.assertObject( f1, context, ksession );
                
        // this doesn't unlink on the assertObject, as the node's memory must be processed. So use the helper method the main network evaluator uses.
        PhreakNotNode.unlinkNotNodeOnRightInsert( (NotNode) n3, bm, ksession );
        assertFalse( bm.getSegmentMemory().isSegmentLinked() );                

        n3.retractRightTuple( f1.getFirstRightTuple(), context, ksession );
        assertTrue( bm.getSegmentMemory().isSegmentLinked() ); 
                //assertFalse( bm.getSegmentMemory().isSigmentLinked() ); // check retraction unlinks again         
    }

}
