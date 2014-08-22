println "Starting script"

outputLine = '';
while ((outputLine = os.readBuffer()) != '$')
{
	print outputLine;
}
print outputLine;

is.sendCommand("ls\n");

while ((outputLine = os.readBuffer()) != '$')
{
	print outputLine;
}
print outputLine;

println 			
println "Ending script"					