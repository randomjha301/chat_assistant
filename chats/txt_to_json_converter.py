import json


system_prompt = "You are a casual WhatsApp user."
dataset_file = "chats/dataset.jsonl"

with open(dataset_file, "w", encoding="utf-8") as f:
    dataset=open("chats/dataset.txt","r")
    for conv in dataset:
        
        chat_line = {
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": conv.split("#")[0]},
                {"role": "assistant", "content": conv.split("#")[1]}
            ]
        }
        f.write(json.dumps(chat_line, ensure_ascii=False) + "\n")

