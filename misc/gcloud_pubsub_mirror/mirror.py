"""
Mirrors a pub/sub topic to another pub/sub topic by consuming a subscription
and republishing all messages
"""

import argparse
import datetime
import base64
import json
import os
import sys

from concurrent import futures
from functools import partial
from google import auth
from google.cloud import pubsub_v1

messages_processed = 0
messages = False
publish_futures = []
tests_index = 0

def is_trace_project(target: str) -> bool:
  return target == '//'

def is_tests_project(target: str) -> bool:
  return target == '++'

def subscribe_callback(message: pubsub_v1.subscriber.message.Message) -> None:
  global messages_processed
  publish(message)
  messages_processed += 1
  if messages_processed % 100 == 0:
    print(f'{messages_processed} messages processed')
  message.ack()

def get_publish_callback():
  def callback(publish_future: pubsub_v1.publisher.futures.Future) -> None:
    try:
      # Wait 60 seconds for the publish call to succeed.
      publish_future.result(timeout=60)
    except futures.TimeoutError:
      print('Publish timed out.')
  return callback

def topic_publisher(pubsub_sink, topic, message):
  print('Publishing', topic, message.publish_time)
  publish_future = pubsub_sink.publish(topic, message.data,
                                       **message.attributes)
  publish_future.add_done_callback(get_publish_callback())
  publish_futures.append(publish_future)

def tests_publisher(path: str, message):
  global tests_index
  tests_index += 1
  os.makedirs(path, exist_ok = True)

  subType = message.attributes.get('subType') or 'event'
  subFolder = message.attributes.get('subFolder') or 'unknown'
  file_name = '%s/%03d_%s_%s.json' % (path, tests_index, subType, subFolder)

  print('Writing tests file ' + file_name)
  message_obj = json.loads(message.data.decode('utf-8'))
  with open(file_name, 'w', encoding='utf-8') as outfile:
    outfile.write(json.dumps(message_obj, indent=2))

  if tests_index >= 100:
    raise Exception('Stopping test output after %d messages' % (tests_index))

def trace_publisher(path: str, message):
  fullstamp = message.publish_time.isoformat() + 'Z'
  timestamp = fullstamp.replace('+00:00Z', 'Z').replace('000Z', 'Z')
  timepath = timestamp[0: timestamp.rindex(':')].replace(':', '/')
  file_path = f'{path}/{timepath}'
  os.makedirs(file_path, exist_ok = True)

  file_name = f'{file_path}/{timestamp}_{message.message_id}.json'

  message_dict = {
    'data': base64.b64encode(message.data).decode('utf-8'),
    'publish_time': timestamp,
    'attributes': dict(message.attributes)
  }

  print('Writing trace file ' + file_name)
  with open(file_name, 'w', encoding='utf-8') as outfile:
    outfile.write(json.dumps(message_dict, indent=2))

def load_tests(path: str):
  raise Exception('tests loader not yet implemented')

def load_traces(path: str):
  print('Processing ' + path)
  if os.path.isfile(path):
    with open(path, 'r', encoding='utf-8') as json_file:
      yield json.load(json_file)
  elif os.path.isdir(path):
    dirs = sorted(os.listdir(path))
    for subdir in dirs:
      yield from load_traces(path + '/' + subdir)
  else:
    raise Exception('Unknown file ' + path)

class Message:
  pass

def noop_ack():
  pass

def message_reader(message_generator, callback):
  global has_messages
  message = next(message_generator, None)
  if not message:
    has_messages = False
    return
  obj = Message()
  obj.data = base64.b64decode(message['data'])
  publish_time = message['publish_time'].replace('Z', '')
  obj.publish_time = datetime.datetime.fromisoformat(publish_time)
  obj.attributes = message['attributes']
  obj.ack = noop_ack
  callback(obj)

def parse_command_line_args():
  parser = argparse.ArgumentParser()
  parser.add_argument('source_project', type=str)
  parser.add_argument('source_subscription', type=str)
  parser.add_argument('target_project', type=str)
  parser.add_argument('target_topic', type=str)
  return parser.parse_args()

args = parse_command_line_args()

try:
  print('authenticating user')
  credentials, project_id = auth.default()
# pylint: disable-next=broad-except
except Exception as e:
  print(e)
  sys.exit()

is_trace = is_trace_project(args.source_project)
if is_trace or is_tests_project(args.source_project):
  source = args.source_subscription
  messages = load_traces(source) if is_trace else load_tests(source)
  get_messages = partial(message_reader, messages, subscribe_callback)
  future = None
  has_messages = True
else:
  subscriber = pubsub_v1.SubscriberClient(credentials=credentials)
  subscription = subscriber.subscription_path(args.source_project,
                                              args.source_subscription)
  future = subscriber.subscribe(subscription, subscribe_callback)
  print('Listening to pubsub, please wait ...')
  get_messages = partial(future.result, timeout=10)
  has_messages = True

if is_trace_project(args.target_project):
  publish = partial(trace_publisher, args.target_topic)
elif is_tests_project(args.target_project):
  publish = partial(tests_publisher, args.target_topic)
else:
  publisher = pubsub_v1.PublisherClient(credentials=credentials)
  topic_path = publisher.topic_path(args.target_project, args.target_topic)
  publish = partial(topic_publisher, publisher, topic_path)


while has_messages:
  try:
    get_messages()
  except (futures.CancelledError, KeyboardInterrupt, futures.TimeoutError):
    print('Ending message loop due to normal termination')
    break
  # pylint: disable-next=broad-except
  except Exception as ex:
    print(f'Message loop failed with error: {ex}')
    break

if future:
  future.cancel()
  future.result()

print('Waiting for message publishing to complete...')
futures.wait(publish_futures, return_when=futures.ALL_COMPLETED)
