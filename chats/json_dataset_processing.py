import json
import numpy as np




def shuffle_dataset(input_file,output_file):
    t=[]
    with open(input_file,'r',encoding='utf-8') as f:
        for line in f:
            line=line.strip()
            if not line:
                continue

            t.append(json.loads(line))

    t=np.array(t)
    rng=np.random.default_rng()
    rng.shuffle(t)
    with open(output_file,'w',encoding='utf-8') as f:
        for line in t:
            str=json.dumps(line,ensure_ascii=False)

            f.write(str+'\n')

def remove_extreme_length_chats(input_file,output_file):
    data=[]
    with open(input_file,'r',encoding='utf-8') as f:
        for line in f:
            dict=json.loads(line)
            if len(dict['messages'][1]["content"])>10 and len(dict['messages'][1]["content"])<140 and len(dict['messages'][2]["content"])<140 :
                data.append(dict)
            
    with open(output_file,'w',encoding='utf-8') as f:
        for line in data:
            str=json.dumps(line,ensure_ascii=False)
            f.write(str+"\n")


                
def remove_garbage_data(input_file):
    forbidden_strings = [
        "<This message was edited>",
        "This message was deleted",
        "(file attached)"
    ]

    cleaned_data = []
    removed_count = 0

    with open(input_file, 'r', encoding='utf-8') as infile:
        for line in infile:
            data = json.loads(line)
            contains_forbidden = False
            
            for message in data.get('messages', []):
                content = message.get('content', '')
                if any(bad_string in content for bad_string in forbidden_strings):
                    contains_forbidden = True
                    break 
            
           
            if contains_forbidden:
                removed_count += 1
            else:
                cleaned_data.append(data)

    with open(input_file, 'w', encoding='utf-8') as outfile:
        for item in cleaned_data:
            outfile.write(json.dumps(item, ensure_ascii=False) + '\n')

    print("Filtering complete!")
    print(f"Removed {removed_count} conversations.")
    print(f"Kept {len(cleaned_data)} clean conversations.")




if __name__=="__main__":
    input_file='chats/dataset.jsonl'
    output_file='chats/shuffled_dataset.jsonl'
    remove_extreme_length_chats(input_file,output_file)
    remove_garbage_data(output_file)

            
    


