import re,json

def format_file(input_filename):
    pattern=r"^\d{2}/\d{2}/\d{4}"
    with open(input_filename, "r") as f:
        lines = f.readlines()
    

    for i,line in enumerate(lines):
        if re.match(r".*<Media omitted>.*",line):
            del lines[i]
        if re.match(pattern,line) is None:
            lines[i-1]=lines[i-1].rstrip("\r\n")+" "+lines[i].strip()+"\n"
            del lines[i]

    with open(input_filename,"w") as f:
        f.writelines(lines)

def extract_chats(input_filename,output_filename):
    with open(input_filename, "r") as f:
        lines = f.readlines()
    output_file=open(output_filename,"a")

    prev_date=lines[1][0:10]
    prev_name=re.search(r"^.*- (.*):.*$",lines[1]).group(1).strip()
    chat=""

    for line in lines:
        date=line[0:10]
        name=re.search(r"^.*- (.*):.*$",line)

        if name:
            text=line.split(":")[2].strip()
            name=name.group(1).strip()

            if date==prev_date:
                if name==prev_name:
                    chat+=","+text
                else:
                    output_file.write(f"{chat.lstrip(",")}\n")
                    chat=text
            else:
                output_file.write(f"{chat.lstrip(",")}\n")
                chat=text

            prev_date=date
            prev_name=name

    output_file.write(f"{chat.lstrip(",")}\n")
    output_file.close()
        
def generate_chat_pairs(output_filename):
    with open(output_filename,"r") as f:
        lines=f.readlines()

    for i,line in enumerate(lines):
        if i<len(lines)-1:
            lines[i]=lines[i].strip()+" # "+lines[i+1].strip()+"\n"
        else:
            del lines[i]
        
    with open(output_filename,"w") as f:
        f.writelines(lines)
    

        

            

def main():
    filename=[]
    output_filename="chats/dataset.txt"

    for name in filename:
        name="chats/WhatsApp Chat with "+name+".txt"
        format_file(name)
        extract_chats(name,output_filename)
    
    generate_chat_pairs(output_filename)


  

            


if __name__=="__main__":
    main()




