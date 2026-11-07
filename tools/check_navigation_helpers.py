"""Host-only assertions for navigation safeguards. No ADB command is permitted here."""
import copy
import unittest
import xml.etree.ElementTree as E
from unittest.mock import patch
import device as d

class NavigationHelpers(unittest.TestCase):
 def test_back_action_is_not_the_toolbar_or_system_back_button(self):
  def node(cls,label):return E.Element('node',{'class':cls,'content-desc':label})
  self.assertFalse(d._match(node('android.widget.ImageView','返回'),'返回',True,True))
  self.assertFalse(d._match(node('android.widget.FrameLayout','返回'),'返回',True,True))
  self.assertTrue(d._match(node('android.widget.RadioButton','返回'),'返回',True,True))
  self.assertTrue(d._match(node('android.widget.RadioButton','启动指定应用，设置'),'启动指定应用',True,True))
 def setUp(self):
  self.state={'enabled':True,'left_enabled':True,'right_enabled':True,'action_right_straight_short':'back'}
  self.calls=[]
  self.patches=[patch.object(d,'sh',side_effect=AssertionError('Host test must never access ADB')),
   patch.object(d,'preferences',side_effect=lambda *args:copy.deepcopy(self.state)),
   patch.object(d,'main_home',side_effect=lambda:self.calls.append(('home',))),
   patch.object(d,'click_item',side_effect=lambda *args:self.calls.append(('click',)+args)),
   patch.object(d,'wait_node',return_value=None)]
  for p in self.patches:p.start()
 def tearDown(self):
  for p in reversed(self.patches):p.stop()
 def test_registry_is_read_from_current_source(self):self.assertEqual(d.action_titles()['back'],'返回')
 def test_pair_count_preserves_enabled_legacy_extra(self):self.assertEqual(d.pair_count({'extra2_left_enabled':True}),3)
 def test_disabled_handle_is_not_clicked_or_enabled(self):
  self.state['right_enabled']=False
  with self.assertRaisesRegex(AssertionError,'Handle is disabled'):d.open_handle('right')
  self.assertFalse(any(c[0]=='click' for c in self.calls));self.assertFalse(self.state['right_enabled'])
 def test_disabled_master_is_not_enabled(self):
  self.state['enabled']=False
  with self.assertRaisesRegex(AssertionError,'master is disabled'):d.open_handle('left')
  self.assertFalse(any(c[0]=='click' for c in self.calls))
 def test_handle_route_uses_real_description(self):
  d.open_handle('right');self.assertIn(('click','右侧触钮',True),self.calls)
 def test_short_direction_is_unambiguous(self):
  d.open_action_chooser('right');self.assertIn(('click','短滑直线向左',True),self.calls)
 def test_long_disabled_does_not_open_chooser(self):
  self.state['right_long_enabled']=False
  with self.assertRaisesRegex(AssertionError,'Long swipe is disabled'):d.open_action_chooser('right',longer=True)
  self.assertFalse(any('长滑' in str(c) for c in self.calls))
 def test_gear_uses_an_enabled_handle_and_preserves_mapping(self):
  self.state['right_enabled']=False;before=copy.deepcopy(self.state)
  d.action_settings('任务切换器')
  self.assertIn(('click','左侧触钮',True),self.calls);self.assertIn(('click','设置任务切换器',True),self.calls);self.assertEqual(self.state,before)
 def test_no_enabled_handle_means_no_navigation(self):
  self.state.update(left_enabled=False,right_enabled=False)
  with self.assertRaisesRegex(AssertionError,'No enabled handle'):d.action_settings('快速工具')
  self.assertEqual(self.calls,[])
 def test_old_preview_route_opens_gear_then_preview(self):
  d.main_link('任务切换器');self.assertIn(('click','设置任务切换器',True),self.calls);self.assertIn(('click','预览任务切换器',True),self.calls)
 def test_old_settings_route_does_not_open_preview(self):
  d.main_link('导航键设置');self.assertIn(('click','设置导航键面板',True),self.calls);self.assertNotIn(('click','预览导航键',True),self.calls)
 def test_mapping_restored_to_original_not_fixed_back(self):
  self.state['action_right_straight_short']='home'
  def restore(title,*args):self.assertEqual(title,'主屏幕');self.state['action_right_straight_short']='home'
  with patch.object(d,'choose_action',side_effect=restore):
   with d.preserved_action():self.state['action_right_straight_short']='screen_magnifier'
  self.assertEqual(self.state['action_right_straight_short'],'home')
 def test_custom_mapping_is_rejected_before_test_mutation(self):
  self.state['action_right_straight_short']='app:com.example/.Main'
  with self.assertRaisesRegex(AssertionError,'refusing to overwrite'):
   with d.preserved_action():self.fail('Must not enter mutation block')
 def test_unknown_legacy_route_is_explicit(self):
  with self.assertRaisesRegex(AssertionError,'No OHO navigation route'):d.main_link('不存在的旧入口')
 def test_scroll_uses_short_slow_motion(self):
  tree=E.fromstring('<hierarchy><node class="android.widget.ScrollView" bounds="[0,100][1000,2100]"/></hierarchy>')
  with patch.object(d,'sh') as shell,patch.object(d.time,'sleep'):
   d._scroll(tree);shell.assert_called_once_with('input','swipe','500','1500','500','700','450')
 def test_offscreen_target_above_reverses_without_fling(self):
  hidden=E.fromstring('<hierarchy><node class="android.widget.ScrollView" bounds="[0,100][1000,2100]"><node content-desc="设置快速工具" bounds="[0,-200][1000,-100]"/></node></hierarchy>')
  shown=E.fromstring('<hierarchy><node class="android.widget.ScrollView" bounds="[0,100][1000,2100]"><node content-desc="设置快速工具" bounds="[0,200][1000,300]"/></node></hierarchy>')
  with patch.object(d,'ui',side_effect=[hidden,hidden,shown]),patch.object(d,'_scroll') as scroll:
   self.assertEqual(d.find_item('设置快速工具',True,timeout=0).get('bounds'),'[0,200][1000,300]')
   scroll.assert_called_once_with(hidden,False)

if __name__=='__main__':unittest.main()
