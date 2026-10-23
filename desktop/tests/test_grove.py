import unittest
from cybercat.grove import grove_layout
class GroveTests(unittest.TestCase):
    def test_handoff_preserves_previous_tree_and_interpolates(self):
        layers,active,alpha=grove_layout(1,0)
        self.assertEqual((layers[0].x,layers[0].scale,alpha),(55,1,0))
        layers,_,alpha=grove_layout(1,1.2)
        self.assertAlmostEqual(layers[0].x,50);self.assertAlmostEqual(alpha,.5)
    def test_second_boundary_and_reduced_motion(self):
        layers,_,_=grove_layout(2,0)
        self.assertEqual([t.x for t in layers],[45,66])
        self.assertEqual(grove_layout(1,0,False)[0][0].x,45)
        self.assertEqual(grove_layout(100,10)[0][0].alpha,0)
        self.assertEqual(len(grove_layout(100,10)[0]),4)
        self.assertAlmostEqual(grove_layout(1,2.88,True,True)[2],.5)
