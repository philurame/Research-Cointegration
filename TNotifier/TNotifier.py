import os, signal, subprocess
import time
import threading
from telegram import Update
from telegram.ext import Updater, CommandHandler, CallbackContext

BOT_TOKEN = '???'
monitor_thread = None
monitoring = False
pid_to_monitor = None
allowed_users = set([283460642]) 

def execute(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  try:
    command = ' '.join(context.args).strip()
    res = subprocess.check_output(command, shell=True)
    update.message.reply_text(res.decode('utf-8'))
  except Exception as e:
    update.message.reply_text(f"Error: {e}")

def add_user(update: Update, context: CallbackContext) -> None:
  user_id = update.effective_user.id
  if user_id == 283460642:
    try:
      new_user_id = int(context.args[0])
      allowed_users.add(new_user_id)
      update.message.reply_text(f"User with ID {new_user_id} has been added.")
    except (IndexError, ValueError):
      update.message.reply_text("Invalid command. Use /add_user <user_id> to add a new user.")
  else:
      update.message.reply_text("Sorry, only the bot owner can add new users.")

def start(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  update.message.reply_text("Bot started. Use /monitor <pid> to start monitoring a process.")

def monitor(update: Update, context: CallbackContext) -> None:
  global monitoring, pid_to_monitor, monitor_thread, allowed_users

  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return

  if monitoring:
    update.message.reply_text("Already monitoring a process. Use /stop_monitor to stop the current monitoring.")
    return
  try:
    pid_to_monitor = int(context.args[0])
    monitoring = True
    monitor_thread = threading.Thread(target=monitor_process, args=(update, context))
    monitor_thread.start()
    update.message.reply_text(f"Started monitoring process with PID {pid_to_monitor}.")
  except (IndexError, ValueError):
    update.message.reply_text("Invalid command. Use /monitor <pid> to start monitoring a process.")

def stop_monitor(update: Update, context: CallbackContext) -> None:
  global monitoring, pid_to_monitor, allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
    
  if monitoring:
    monitoring = False
    monitor_thread.join()
    update.message.reply_text(f"Stopped monitoring process with PID {pid_to_monitor}.")
    pid_to_monitor = None
  else:
    update.message.reply_text("No process is being monitored currently.")

def stop_bot(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  update.message.reply_text("Stopping the bot...")
  updater.stop()
  pid_bot = os.getpid()
  os.kill(pid_bot, signal.SIGTERM)

def restart_bot(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  update.message.reply_text("Restarting the bot...")
  updater.stop()
  os.system("./TNotifier.py")


def status(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  if monitoring:
    update.message.reply_text(f"Currently monitoring process with PID {pid_to_monitor}.")
  else:
    update.message.reply_text("No process is being monitored currently.")

def cat(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  try:
    file_path = ' '.join(context.args).strip()
    file_content = _read_file_content(file_path)[-4095:]
    update.message.reply_text(file_content)
  except Exception as e:
    update.message.reply_text(f"Error: {e}")

def cat_full(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  try:
    file_path = ' '.join(context.args).strip()
    file_content = _read_file_content(file_path)
    for i in range(0,len(file_content)//4095+1):
      if file_content[i*4095:(i+1)*4095]:
        update.message.reply_text(file_content[i*4095:(i+1)*4095])
  except Exception as e:
    update.message.reply_text(f"Error: {e}")

def ls(update: Update, context: CallbackContext) -> None:
  global allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  try:
    full_path = ' '.join(context.args).strip()
    dirs_content = os.listdir(full_path)
    update.message.reply_text(dirs_content)
  except Exception as e:
    update.message.reply_text(f"Error: {e}")
  

def monitor_process(update: Update, context: CallbackContext) -> None:
  global monitoring, pid_to_monitor, allowed_users
  if update.effective_user.id not in allowed_users:
    update.message.reply_text("Sorry, you are not authorized to use this bot.")
    return
  while monitoring and pid_to_monitor:
    if not _check_process(pid_to_monitor):
      context.bot.send_message(chat_id=update.effective_chat.id, text=f"Process with PID {pid_to_monitor} has terminated.")
      monitoring = False
      break
    time.sleep(5)

def _check_process(pid):
  try:
    os.kill(pid, 0)
  except OSError:
    return False
  return True

def _read_file_content(file_path):
  try:
    with open(file_path, 'r') as file:
      return file.read()
  except Exception as e:
    return f"Error reading file: {e}"

def main():
  global updater
  updater = Updater(BOT_TOKEN)
  dispatcher = updater.dispatcher

  dispatcher.add_handler(CommandHandler("start", start))
  dispatcher.add_handler(CommandHandler("monitor", monitor))
  dispatcher.add_handler(CommandHandler("stop_monitor", stop_monitor))
  dispatcher.add_handler(CommandHandler("status", status))
  dispatcher.add_handler(CommandHandler("cat", cat))
  dispatcher.add_handler(CommandHandler("cat_full", cat_full))
  dispatcher.add_handler(CommandHandler("ls", ls))
  dispatcher.add_handler(CommandHandler("add_user", add_user))
  dispatcher.add_handler(CommandHandler("stop_bot", stop_bot))
  dispatcher.add_handler(CommandHandler("restart_bot", restart_bot))
  dispatcher.add_handler(CommandHandler("exec", execute))

  updater.start_polling()
  updater.idle()

if __name__ == "__main__":
  main()
