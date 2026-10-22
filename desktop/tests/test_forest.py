import unittest,tempfile
from cybercat.forest import trees,garden,tree_text,summary,growth
from cybercat.model import Store
class ForestTests(unittest.TestCase):
    def test_resume_finished_excludes_gap_and_keeps_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            now=[0.];wall=[1700000000.];timer=Store(directory,lambda:now[0],lambda:wall[0])
            timer.start();now[0]+=1200;timer.finish();identifier=timer.session_id
            now[0]+=3600;timer.resume_finished();now[0]+=10;timer.resume_finished();now[0]+=10;timer.finish()
            self.assertEqual(timer.elapsed,1220);self.assertEqual(timer.session_id,identifier)
            timer.save('Continued');self.assertEqual(len(timer.sessions),1);self.assertEqual(timer.sessions[0]['duration'],1220)
    def test_milestones(self):
        for seconds,count in [(0,0),(1799,0),(1800,0),(3599,0),(3600,1),(5400,1),(7200,2),(28799,7),(28800,8),(28801,8)]:
            self.assertEqual(trees(seconds),count)
            self.assertEqual(garden(seconds),seconds>=28800)
        self.assertEqual(growth(1800),.5); self.assertEqual(growth(5400),.5)
        self.assertEqual(trees(float('nan')),0)
    def test_fragments_and_garden_days(self):
        self.assertEqual(tree_text(1800+1800),'1 tree')
        self.assertEqual(summary(28800,[3600]*8),'8 trees')
        self.assertEqual(summary(28800,[28800]),'8 trees · 1 garden day')
    def test_saved_and_live_growth_remains_stable(self):
        with tempfile.TemporaryDirectory() as d:
            now=[1000.]; wall=[1700000000.]
            s=Store(d,lambda:now[0],lambda:wall[0]);s.start();now[0]+=1800;wall[0]+=1800;s.finish();s.save('Half')
            self.assertEqual(s.growth_elapsed,1800)
            s.start();now[0]+=1800;wall[0]+=1800;s.pause();self.assertEqual(s.growth_elapsed,3600)
            now[0]+=100;self.assertEqual(s.growth_elapsed,3600)
            s.finish();s.save('Half two');self.assertEqual(s.growth_elapsed,3600)
            s.delete(s.sessions[0]['id']);self.assertEqual(s.growth_elapsed,1800)
